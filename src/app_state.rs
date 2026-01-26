
use redis::Client as RedisClient;
use sea_orm::DatabaseConnection;
use serde_json::Value;

// --- AppState ---
#[derive(Clone)]
pub struct AppState {
    pub db: DatabaseConnection,
    pub redis_client: RedisClient,
    pub filter_events_config: Value,
    pub message_center_url: String,
    pub dq_service_url: String,
}