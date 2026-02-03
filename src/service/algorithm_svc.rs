use anyhow::Result;
use once_cell::sync::Lazy;
use sea_orm::{DbConn, DbErr, EntityTrait};
use redis::{Client, Commands};
use tokio::sync::Mutex;
use tracing;

use crate::models::{prelude::*, algorithm};

const CACHE_KEY_ALGORITHMS: &str = "event_rs::all_algorithms";
const CACHE_TTL: u64 = 180; // 3 minutes in seconds
static ALGORITHMS_FETCH_LOCK: Lazy<Mutex<()>> = Lazy::new(|| Mutex::new(()));


/// Fetches all algorithms, using a cache with a 3-minute TTL.
pub async fn get_all_algorithms(
    db: &DbConn,
    redis_client: &Client,
) -> Result<Vec<algorithm::Model>> {
    let mut redis_conn = redis_client.get_connection()?;
    
    // 1. Try to fetch from cache
    if let Ok(cached_algorithms) = redis_conn.get::<_, String>(CACHE_KEY_ALGORITHMS) {
        if !cached_algorithms.is_empty() {
            if let Ok(algorithms) = serde_json::from_str(&cached_algorithms) {
                return Ok(algorithms);
            }
        }
    }

    // 2. If not in cache, acquire lock to prevent cache stampede
    let _lock = ALGORITHMS_FETCH_LOCK.lock().await;

    // Re-check cache after acquiring lock, another request might have populated it
    if let Ok(cached_algorithms) = redis_conn.get::<_, String>(CACHE_KEY_ALGORITHMS) {
        if !cached_algorithms.is_empty() {
            if let Ok(algorithms) = serde_json::from_str(&cached_algorithms) {
                return Ok(algorithms);
            }
        }
    }

    // 3. If still not in cache, query the database
    let algorithms = Algorithm::find().all(db).await?;

    // 4. Store in cache
    if let Ok(algorithms_json) = serde_json::to_string(&algorithms) {
        let _: () = redis_conn.set_ex(CACHE_KEY_ALGORITHMS, algorithms_json, CACHE_TTL).unwrap_or_default();
    }

    Ok(algorithms)
}

/// Finds an algorithm by its code from the cached full list.
pub async fn find_algorithm_by_code(
    db: &DbConn,
    redis_client: &Client,
    code: &str,
) -> Result<Option<algorithm::Model>> {
    let algorithms = get_all_algorithms(db, redis_client).await?;
    Ok(algorithms.into_iter().find(|a| a.code.as_deref() == Some(code)))
}