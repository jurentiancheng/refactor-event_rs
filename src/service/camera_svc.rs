use sea_orm::{DbConn, DbErr, EntityTrait};
use redis::{Client, Commands};

use crate::models::{prelude::*, camera};

const CACHE_KEY_CAMERAS: &str = "all_cameras";
const CACHE_TTL: u64 = 180; // 3 minutes in seconds

/// Fetches all cameras, using a cache with a 3-minute TTL.
pub async fn get_all_cameras(
    db: &DbConn,
    redis_client: &Client,
) -> Result<Vec<camera::Model>, DbErr> {
    let mut redis_conn = redis_client.get_connection().map_err(|e| DbErr::Custom(e.to_string()))?;

    // 1. Try to fetch from cache
    if let Ok(cached_cameras) = redis_conn.get::<_, String>(CACHE_KEY_CAMERAS) {
        if !cached_cameras.is_empty() {
            if let Ok(cameras) = serde_json::from_str(&cached_cameras) {
                return Ok(cameras);
            }
        }
    }

    // 2. If not in cache, query the database
    let cameras = Camera::find().all(db).await?;

    // 3. Store in cache
    if let Ok(cameras_json) = serde_json::to_string(&cameras) {
        let _: () = redis_conn.set_ex(CACHE_KEY_CAMERAS, cameras_json, CACHE_TTL).unwrap_or_default();
    }

    Ok(cameras)
}

#[derive(Default)]
pub struct CameraSearchCriteria<'a> {
    pub code: Option<&'a str>,
    pub box_id: Option<i64>,
    pub box_sn: Option<&'a str>,
    pub project_id: Option<i64>,
}

/// Finds cameras by criteria from the cached full list.
pub async fn find_cameras_by_criteria<'a>(
    db: &DbConn,
    redis_client: &Client,
    criteria: CameraSearchCriteria<'a>,
) -> Result<Vec<camera::Model>, DbErr> {
    let cameras = get_all_cameras(db, redis_client).await?;
    
    let filtered_cameras = cameras.into_iter().filter(|c| {
        if let Some(code) = criteria.code {
            if c.code.as_deref() != Some(code) { return false; }
        }
        if let Some(box_id) = criteria.box_id {
            if c.box_id != Some(box_id) { return false; }
        }
        if let Some(box_sn) = criteria.box_sn {
            if c.box_sn.as_deref() != Some(box_sn) { return false; }
        }
        if let Some(project_id) = criteria.project_id {
            if c.project_id != Some(project_id) { return false; }
        }
        true
    }).collect();

    Ok(filtered_cameras)
}