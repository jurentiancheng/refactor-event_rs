use anyhow::Result;
use once_cell::sync::Lazy;
use sea_orm::{DbConn, DbErr, EntityTrait};
use redis::{Client, Commands};
use tokio::sync::Mutex;


use crate::models::{prelude::*, event_filter_config};

const CACHE_KEY_EVENT_FILTER_CONFIGS: &str = "all_event_filter_configs";
const CACHE_TTL: u64 = 180; // 3 minutes in seconds
static EVENT_FILTER_CONFIGS_FETCH_LOCK: Lazy<Mutex<()>> = Lazy::new(|| Mutex::new(()));

/// Fetches all event filter configs, using a cache with a 3-minute TTL.
pub async fn get_all_event_filter_configs(
    db: &DbConn,
    redis_client: &Client,
) -> Result<Vec<event_filter_config::Model>> {
    let mut redis_conn = redis_client.get_connection().map_err(|e| DbErr::Custom(e.to_string()))?;
    
    // 1. Try to fetch from cache
    if let Ok(cached_configs) = redis_conn.get::<_, String>(CACHE_KEY_EVENT_FILTER_CONFIGS) {
        if !cached_configs.is_empty() {
            if let Ok(configs) = serde_json::from_str(&cached_configs) {
                return Ok(configs);
            }
        }
    }

    // 2. If not in cache, acquire lock to prevent cache stampede
    let _lock = EVENT_FILTER_CONFIGS_FETCH_LOCK.lock().await;

    // Re-check cache after acquiring lock
    if let Ok(cached_configs) = redis_conn.get::<_, String>(CACHE_KEY_EVENT_FILTER_CONFIGS) {
        if !cached_configs.is_empty() {
            if let Ok(configs) = serde_json::from_str(&cached_configs) {
                return Ok(configs);
            }
        }
    }
    
    // 3. If still not in cache, query the database
    let configs = EventFilterConfig::find().all(db).await?;

    // 4. Store in cache
    if let Ok(configs_json) = serde_json::to_string(&configs) {
        let _: () = redis_conn.set_ex(CACHE_KEY_EVENT_FILTER_CONFIGS, configs_json, CACHE_TTL).unwrap_or_default();
    }

    Ok(configs)
}

/// Finds event filter configs by project_id from the cached full list.
pub async fn find_event_filter_configs_by_project_id(
    db: &DbConn,
    redis_client: &Client,
    project_id: i64,
) -> Result<Vec<event_filter_config::Model>> {
    let configs = get_all_event_filter_configs(db, redis_client).await?;
    Ok(configs
        .into_iter()
        .filter(|c| c.project_id == Some(project_id))
        .collect())
}