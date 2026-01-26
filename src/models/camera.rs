use sea_orm::entity::prelude::*;
use chrono::NaiveDateTime;
use serde_json::Value as Json;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, PartialEq, DeriveEntityModel, Serialize, Deserialize)]
#[sea_orm(table_name = "camera")]
pub struct Model {
    #[sea_orm(primary_key)]
    #[serde(skip_deserializing)]
    pub id: i64,
    pub code: Option<String>,
    pub name: Option<String>,
    #[sea_orm(column_name = "type")]
    pub camera_type: Option<i32>,
    pub status: Option<i32>,
    pub preset_able: Option<i32>,
    pub obtain_able: Option<i32>,
    pub set_able: Option<i32>,
    pub snapshot_able: Option<i32>,
    pub cache_limit: Option<i32>,
    #[sea_orm(column_type = "Json", nullable)]
    pub video: Option<Json>,
    #[sea_orm(column_type = "Json", nullable)]
    pub extra: Option<Json>,
    pub stream_status: Option<i32>,
    pub stream_time: Option<NaiveDateTime>,
    pub ptz_type: Option<i32>,
    pub box_id: Option<i64>,
    #[sea_orm(column_type = "Json", nullable)]
    pub box_snap: Option<Json>,
    pub box_sn: Option<String>,
    pub box_name: Option<String>,
    pub alias_name: Option<String>,
    #[sea_orm(column_type = "Json", nullable)]
    pub attribute: Option<Json>,
    #[sea_orm(column_type = "Json", nullable)]
    pub addition_attribute: Option<Json>,
    pub thirdparty_id: Option<i64>,
    pub project_id: Option<i64>,
    pub project_name: Option<String>,
    #[sea_orm(default_expr = "Expr::current_timestamp()")]
    pub create_time: NaiveDateTime,
    #[sea_orm(default_expr = "Expr::current_timestamp()", on_update = "Expr::current_timestamp()")]
    pub update_time: NaiveDateTime,
    pub create_by: Option<i64>,
    pub update_by: Option<i64>,
    #[sea_orm(default_value = "0")]
    pub is_del: i32,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceAttribute {
    pub port: Option<i32>,
    pub transport: Option<i32>,
    pub name: Option<String>,
    pub discovery_protocol: Option<i32>,
    pub ip: Option<String>,
    pub equipment_manufacturer: Option<i32>,
    pub receiving_equipment: Option<i32>,
    pub account: Option<String>,
    pub password: Option<String>,
    pub channel_type: Option<i32>,
    pub internal_channel: Option<i32>,
    pub channel_no: Option<i32>,
    pub upstream_url: Option<String>,
    pub stream_type: Option<i32>,
    pub vendor: Option<i32>,
    pub description: Option<String>,
    pub internal_play_url: Option<String>,
    pub public_play_url: Option<String>,
    pub ptz_type: Option<i32>,
    pub zoom: Option<i32>,
    pub ptz: Option<Vec<i32>>,
    pub onvif_ptz: Option<Vec<f64>>,
    pub snap_url: Option<String>,
    pub field_view: Option<Vec<f64>>,
}

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeviceAdditionAttribute {
    pub device_no: Option<String>,
    pub areas: Option<String>,
    pub longitude: Option<f64>,
    pub latitude: Option<f64>,
    pub address_desc: Option<String>,
    pub road_no: Option<String>,
    pub protocol_type: Option<String>,
    pub ip_address: Option<String>,
    pub account: Option<String>,
    pub password: Option<String>,
    pub back_up1: Option<String>,
    pub back_up2: Option<String>,
}

#[derive(Copy, Clone, Debug, EnumIter, DeriveRelation)]
pub enum Relation {}

impl ActiveModelBehavior for ActiveModel {}
