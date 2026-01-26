use sea_orm::entity::prelude::*;
use chrono::NaiveDateTime;
use serde_json::Value as Json;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "event_0")]
pub struct Model {
    #[sea_orm(primary_key)]
    pub id: i64,
    pub task_code: Option<String>,
    pub task_name: Option<String>,
    pub source: Option<String>,
    pub project_id: Option<i64>,
    pub project_name: Option<String>,
    pub company_id: Option<String>,
    pub company_name: Option<String>,
    pub event_type: Option<String>,
    pub scene_id: Option<i64>,
    pub event_type_name: Option<String>,
    pub event_time: Option<NaiveDateTime>,
    pub end_time: Option<NaiveDateTime>,
    pub marking: Option<String>,
    pub discard_id: Option<i64>,
    pub engine_event_id: Option<String>,
    pub vehicle_type: Option<String>,
    pub plate_number: Option<String>,
    pub plate_color: Option<String>,
    pub special_car_type: Option<String>,
    pub engine_version: Option<String>,
    pub car_in_event: Option<i64>,
    #[sea_orm(column_type = "Json", nullable)]
    pub snapshot: Option<Json>,
    pub snapshot_uri_compress: Option<String>,
    pub snapshot_uri_raw_compress: Option<String>,
    pub snapshot_uri_cover_compress: Option<String>,
    #[sea_orm(column_type = "Json", nullable)]
    pub extra_data: Option<Json>,
    #[sea_orm(default_value = "0")]
    pub is_del: i32,
    pub camera_code: Option<String>,
    pub evidence_status: Option<String>,
    pub evidence_url: Option<String>,
    pub original_violation_index: Option<i32>,
    #[sea_orm(column_type = "Json", nullable)]
    pub extra: Option<Json>,
    pub filtered_type: Option<String>,
    pub marking_time: Option<NaiveDateTime>,
    pub marking_count: Option<i32>,
    #[sea_orm(default_expr = "Expr::current_timestamp()")]
    pub create_time: NaiveDateTime,
    #[sea_orm(default_expr = "Expr::current_timestamp()", on_update = "Expr::current_timestamp()")]
    pub update_time: NaiveDateTime,
    pub create_by: Option<i64>,
    pub update_by: Option<i64>,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}