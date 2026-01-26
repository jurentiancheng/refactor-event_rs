// src/main.rs

use axum::{
    routing::{post},
    Router,
    serve,
};
use sea_orm::Database;
use redis::Client as RedisClient;
use dotenvy::dotenv;
use std::{env, sync::Arc};
use tracing_subscriber::{layer::SubscriberExt, util::SubscriberInitExt};
use tower_http::trace::TraceLayer;
use tokio::net::TcpListener;
mod models;
mod ctl;
mod service;
mod app_state;
mod util;
use crate::app_state::AppState;


#[tokio::main]
async fn main() {
    // --- 1. Setup tracing and env ---
    tracing_subscriber::registry()
        .with(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "RUST_LOG=debug,event_rs=debug,tower_http=debug".into()))
        .with(tracing_subscriber::fmt::layer())
        .init();
    dotenv().ok();

    // --- 2. Initialize connections ---
    // SeaORM
    let database_url = env::var("DATABASE_URL").expect("DATABASE_URL must be set");
    let db = Database::connect(&database_url).await.expect("Failed to connect to database");
    tracing::info!("Connected to database successfully!");

    // Redis
    let redis_url = env::var("REDIS_URL").expect("REDIS_URL must be set");
    let redis_password = env::var("REDIS_PASSWORD").expect("REDIS_PASSWORD must be set");
    let redis_client = RedisClient::open(format!("redis://:{}@{}", redis_password, redis_url)).expect("Failed to connect to Redis");
    tracing::info!("Connected to Redis client successfully!");

    // Filter Events Config
    let filter_events_config_json = env::var("FILTER_EVENTS_CONFIG_JSON")
        .unwrap_or_else(|_| r#"{"is_open":false, "event_types":""}"#.to_string());
    let filter_events_config = serde_json::from_str(&filter_events_config_json)
        .expect("Failed to parse FILTER_EVENTS_CONFIG_JSON");
    tracing::info!("Event filter config loaded: {}", filter_events_config);


    // Message Center URL
    let message_center_url = env::var("MESSAGE_CENTER_URL")
        .expect("MESSAGE_CENTER_URL must be set");
    tracing::info!("Message Center URL: {}", message_center_url);

    // DQ Service URL
    let dq_service_url = env::var("DQ_SERVICE_URL")
        .expect("DQ_SERVICE_URL must be set");
    tracing::info!("DQ Service URL: {}", dq_service_url);

    // --- 3. Create AppState ---
    let app_state = Arc::new(AppState { db, redis_client, filter_events_config, message_center_url, dq_service_url });

    // --- 4. Axum Router ---
    let app = Router::new()
        .route("/box/report", post(ctl::box_report_ctl::post_box_report))
        .with_state(app_state.clone())
        .layer(TraceLayer::new_for_http());

    // --- 5. Start Server ---
    let addr = "127.0.0.1:3000";
    let listener = TcpListener::bind(addr).await.unwrap();
    tracing::info!("listening on {}", addr);
    serve(listener, app.into_make_service()).await.unwrap();
}
