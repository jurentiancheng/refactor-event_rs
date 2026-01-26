use sea_orm::entity::prelude::*;
use chrono::NaiveDateTime;
use serde_json::Value as Json;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "task")]
pub struct Model {
    #[sea_orm(primary_key)]
    #[serde(skip_deserializing)]
    pub id: i64,
    pub code: Option<String>,
    pub name: Option<String>,
    pub camera_code: Option<String>,
    pub camera_name: Option<String>,
    pub stream_url: Option<String>,
    pub camera_url: Option<String>,
    pub effect_stream_url: Option<String>,
    pub effect_write_stream_url: Option<String>,
    pub snapshot: Option<String>,
    #[sea_orm(column_name = "type")]
    pub task_type: Option<String>,
    pub box_id: Option<i64>,
    pub box_sn: Option<String>,
    pub box_task_id: Option<String>,
    pub org_id: Option<i64>,
    pub org_name: Option<String>,
    pub preset: Option<String>,
    pub preset_name: Option<String>,
    pub stream_on: Option<i32>,
    pub feature_on: Option<i32>,
    pub region: Option<String>,
    pub scene_id: Option<i64>,
    pub status: Option<String>,
    pub sub_type: Option<i32>,
    pub evidence_on: Option<i32>,
    #[sea_orm(column_type = "Json", nullable)]
    pub future_crons: Option<Json>,
    pub project_id: Option<i64>,
    pub project_name: Option<String>,
    pub switched_time: Option<NaiveDateTime>,
    #[sea_orm(default_expr = "Expr::current_timestamp()")]
    pub create_time: NaiveDateTime,
    #[sea_orm(default_expr = "Expr::current_timestamp()", on_update = "Expr::current_timestamp()")]
    pub update_time: NaiveDateTime,
    pub create_by: Option<i32>,
    pub update_by: Option<i32>,
    #[sea_orm(default_value = "0")]
    pub is_del: i32,
    #[sea_orm(column_type = "Json", nullable)]
    pub extra: Option<Json>,
    pub led_list: Option<String>,
    pub sound_list: Option<String>,
    pub weigh_list: Option<String>,
    pub camera_list: Option<String>,
    pub weigh_code: Option<String>,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}
