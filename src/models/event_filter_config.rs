use sea_orm::entity::prelude::*;
use chrono::NaiveDateTime;
use serde_json::Value as Json;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "event_filter_config")]
pub struct Model {
    #[sea_orm(primary_key)]
    #[serde(skip_deserializing)]
    // This field was not in the original Java entity.
    // Added because a primary key is required by sea-orm.
    pub id: i64,
    pub project_id: Option<i64>,
    pub setting_group: Option<String>,
    pub group_name: Option<String>,
    #[sea_orm(column_type = "Json", nullable)]
    pub config: Option<Json>,
    pub sort: Option<i32>,
    #[sea_orm(default_expr = "Expr::current_timestamp()")]
    pub create_time: NaiveDateTime,
    #[sea_orm(default_expr = "Expr::current_timestamp()", on_update = "Expr::current_timestamp()")]
    pub update_time: NaiveDateTime,
    pub create_by: Option<i64>,
    pub update_by: Option<i64>,
    #[sea_orm(default_value = "0")]
    pub is_del: i32,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}