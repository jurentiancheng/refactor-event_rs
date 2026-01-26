use sea_orm::{DbConn, DbErr, EntityTrait};
use redis::{Client, Commands};

use crate::models::{prelude::*, base_config};

const CACHE_KEY_BASE_CONFIGS: &str = "all_base_configs";
const CACHE_TTL: u64 = 180; // 3 minutes in seconds

/// Fetches all base configs, using a cache with a 3-minute TTL.
pub async fn get_all_base_configs(
    db: &DbConn,
    redis_client: &Client,
) -> Result<Vec<base_config::Model>, DbErr> {
    let mut redis_conn = redis_client.get_connection().map_err(|e| DbErr::Custom(e.to_string()))?;

    // 1. Try to fetch from cache
    if let Ok(cached_configs) = redis_conn.get::<_, String>(CACHE_KEY_BASE_CONFIGS) {
        if !cached_configs.is_empty() {
            if let Ok(configs) = serde_json::from_str(&cached_configs) {
                return Ok(configs);
            }
        }
    }

    // 2. If not in cache, query the database
    let configs = BaseConfig::find().all(db).await?;

    // 3. Store in cache
    if let Ok(configs_json) = serde_json::to_string(&configs) {
        let _: () = redis_conn.set_ex(CACHE_KEY_BASE_CONFIGS, configs_json, CACHE_TTL).unwrap_or_default();
    }

    Ok(configs)
}

/// Finds base configs by project_id from the cached full list.
pub async fn find_base_configs_by_project_id(
    db: &DbConn,
    redis_client: &Client,
    project_id: i64,
) -> Result<Option<base_config::Model>, DbErr> {
    let configs = get_all_base_configs(db, redis_client).await?;
    Ok(configs
        .into_iter()
        .find(|c| c.project_id == Some(project_id))
    )
}