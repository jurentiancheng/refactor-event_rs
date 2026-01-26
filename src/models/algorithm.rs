use sea_orm::entity::prelude::*;
use serde::{Deserialize, Serialize};
use chrono::NaiveDateTime;
use serde_json::Value as Json;

#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "algorithm")]
pub struct Model {
    #[sea_orm(primary_key)]
    #[serde(skip_deserializing)]
    pub id: i64,
    pub code: Option<String>,
    pub pcode: Option<String>,
    pub enname: Option<String>,
    pub cnname: Option<String>,
    pub status: Option<i32>,
    #[sea_orm(column_type = "Json", nullable)]
    pub draw_config: Option<Json>,
    #[sea_orm(column_type = "Json", nullable)]
    pub editable_config: Option<Json>,
    pub label: Option<String>,
    pub draw_type: Option<String>,
    pub description: Option<String>,
    pub is_large_model: Option<i32>,
    #[sea_orm(column_type = "Json", nullable)]
    pub large_model_conf: Option<Json>,
    pub large_model_code_ref: Option<String>,
    #[sea_orm(default_expr = "Expr::current_timestamp()")]
    pub create_time: Option<NaiveDateTime>,
    #[sea_orm(default_expr = "Expr::current_timestamp()", on_update = "Expr::current_timestamp()")]
    pub update_time: Option<NaiveDateTime>,
    pub create_by: Option<i64>,
    pub update_by: Option<i64>,
    #[sea_orm(default_value = "0")]
    pub is_del: i32,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct LargeModelConfVo {
    pub question: Option<String>,
    pub key_matcher: Option<KeyMatcherVo>,
    pub req_timeout: Option<i32>,
    pub req_retry_count: Option<i32>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
pub struct KeyMatcherVo {
    pub discard_keys: Option<Vec<String>>,
    pub match_keys: Option<Vec<String>>,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}