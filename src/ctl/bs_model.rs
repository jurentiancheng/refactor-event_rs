use chrono::Utc;
use serde::{Deserialize, Serialize};
use serde_json::Value;




// Represents the incoming event data structure.
#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct BoxReportRequest {
    pub id: Option<i64>,
    pub task_code: Option<String>,
    pub source: Option<String>,
    pub event_type: Option<String>,
    #[serde(with = "chrono::serde::ts_milliseconds_option")]
    pub event_time: Option<chrono::DateTime<Utc>>,
    #[serde(with = "chrono::serde::ts_milliseconds_option")]
    pub end_time: Option<chrono::DateTime<Utc>>,
    pub marking: Option<String>,
    pub engine_event_id: Option<String>,
    pub vehicle_type: Option<String>,
    pub plate_number: Option<String>,
    pub plate_color: Option<String>,
    pub special_car_type: Option<String>,
    pub engine_version: Option<String>,
    pub snapshot: Option<Value>,
    pub snapshot_uri_compress: Option<String>,
    pub snapshot_uri_raw_compress: Option<String>,
    pub snapshot_uri_cover_compress: Option<String>,
    pub extra_data: Option<Value>,
    pub camera_code: Option<String>,
    pub evidence_status: Option<String>,
    pub evidence_url: Option<String>,
    pub original_violation_index: Option<i32>,
    pub extra: Option<Value>,
    // --- Fields to be populated internally ---
    #[serde(default)]
    pub project_id: i64,
    #[serde(default)]
    pub project_name: String,
    #[serde(default)]
    pub company_id: i64,
    #[serde(default)]
    pub company_name: String,
}


#[derive(Debug, Deserialize, Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ReviewDataPushVo {
    pub id: i64, // Corresponds to eventId from alarmVo.getId()
    pub task_code: Option<String>,
    pub source: Option<String>,
    pub event_type: Option<String>,
    #[serde(with = "chrono::serde::ts_milliseconds_option")]
    pub event_time: Option<chrono::DateTime<Utc>>,
    #[serde(with = "chrono::serde::ts_milliseconds_option")]
    pub end_time: Option<chrono::DateTime<Utc>>,
    pub marking: Option<String>,
    pub engine_event_id: Option<String>,
    pub vehicle_type: Option<String>,
    pub plate_number: Option<String>,
    pub plate_color: Option<String>,
    pub special_car_type: Option<String>,
    pub engine_version: Option<String>,
    pub snapshot: Option<Value>,
    pub snapshot_uri_compress: Option<String>,
    pub snapshot_uri_raw_compress: Option<String>,
    pub snapshot_uri_cover_compress: Option<String>,
    pub extra_data: Option<Value>,
    pub camera_code: Option<String>,
    pub evidence_status: Option<String>,
    pub evidence_url: Option<String>,
    pub original_violation_index: Option<i32>,
    pub extra: Option<Value>,
    pub project_id: i64,
    pub project_name: String,
    pub company_id: i64,
    pub company_name: String,

    // Fields specifically from Java's ReviewDataPushVo and its construction
    pub position: Option<Value>, // JSONArray in Java, so Value in Rust
    pub task_snapshot: Option<String>,
    pub original_config: Option<Value>, // JSONObject in Java, so Value in Rust
    pub editable: Option<Value>, // JSONArray in Java, so Value in Rust
}