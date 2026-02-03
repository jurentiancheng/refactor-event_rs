use anyhow::{Result, anyhow};
use axum::{extract::State, response::Json};
use chrono::{Duration, Local, TimeZone, Utc};
use redis::Commands;
use serde_json::{Value, json};
use std::sync::Arc;

use crate::{
    app_state::AppState,
    ctl::bs_model::{BoxReportRequest, ReviewDataPushVo},
    models::{event, task},
    service::{
        algorithm_svc, base_config_svc, event_filter_config_svc, event_processing_svc, event_svc,
        task_svc,
    }
};
use event_rs::{ JsonResponse, response_result::{RespResult} };


/// Main entry point for handling event reports from boxes.
/// This function orchestrates the entire event processing pipeline.
pub async fn post_box_report(
    State(app_state): State<Arc<AppState>>,
    Json(mut payload): Json<BoxReportRequest>,
) -> JsonResponse<String> {
    // 获取关键信息日志用
    let logging_engine_event_id = payload.engine_event_id.clone().unwrap_or_default();
    tracing::info!("盒子上报信息: eventDto engine_event_id: {}", logging_engine_event_id);
    // 验证source字段
    if payload.source.is_none() || payload.source.as_deref() == Some("") {
        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: 校验异常：Invalid source",
            logging_engine_event_id
        ))));
    };
    // 验证engin_event_id字段
    let engin_event_id = match payload.engine_event_id.as_deref() {
        Some(id) if !id.is_empty() => id.to_string(),
        _ => {
            return Ok(Json(RespResult::ok_with_msg(format!(
                "盒子上报信息: engin_event_id: {}, RESULT: 校验异常：Invalid engine_event_id",
                logging_engine_event_id
            ))));
        }
    };

    // 初始化redis链接
    let mut redis_conn = app_state
        .redis_client
        .get_connection()
        .map_err(|err| anyhow!("Redis connection error: {}", err))?;

    // 防重放
    if redis::cmd("EXISTS")
        .arg(&engin_event_id)
        .query::<bool>(&mut redis_conn)
        .unwrap_or(false)
    {
        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: 事件已经上报过，无需重复处理",
            logging_engine_event_id
        ))));
    }
    // 获取缓存Task数据
    let task = enrich_payload_with_task_data(&app_state, &mut payload)
        .await
        .map_err(|err| anyhow!("no task cached data, err:{}", err))?;
    // 基础字段赋值
    payload.project_id = task.project_id.unwrap_or(0);
    payload.project_name = task.project_name.clone().unwrap_or_default();

    // 冷却时间逻辑校验
    if handle_cooling_down_filter(&app_state, &payload, &mut redis_conn).await {
        tracing::info!(
            "Event filtered by cooling-down mechanism. Event ID: {}",
            engin_event_id
        );
        let _: Result<(), _> = redis::cmd("SET")
            .arg(&engin_event_id)
            .arg(&engin_event_id)
            .arg("EX")
            .arg(600)
            .query(&mut redis_conn);

        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: Event filtered by cooling-down mechanism",
            logging_engine_event_id
        ))));
    };

    // 检索base_config 配置信息
    let _base_config = base_config_svc::find_base_configs_by_project_id(
        &app_state.db,
        &app_state.redis_client,
        payload.project_id as i64,
    )
    .await
    .unwrap_or_default();

    let filter_configs = event_filter_config_svc::find_event_filter_configs_by_project_id(
        &app_state.db,
        &app_state.redis_client,
        payload.project_id,
    )
    .await
    .unwrap_or_default();

    let (shoud_filter, filter_reason) =
        event_processing_svc::filter_event(&mut payload, &filter_configs, &mut redis_conn);

    if shoud_filter {
        payload.marking = Some("filtered".to_string());
        let mut event_model = event_from_payload(&payload, &task);
        event_model.filtered_type = filter_reason.map(String::from);

        let _ = event_svc::create_event(&app_state.db, event_model)
            .await
            .map_err(|err| anyhow!(err))?;

        push_to_kafka_filtered(app_state.clone(), &payload).await;
        let _: Result<(), _> = redis::cmd("SET")
            .arg(&engin_event_id)
            .arg(&engin_event_id)
            .arg("EX")
            .arg(600)
            .query(&mut redis_conn);
        
        // 如果是过滤事件直接入Kafka队列
        push_to_kafka_filtered(app_state.clone(), &payload).await;
        
        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: Invalid source",
            logging_engine_event_id
        ))));
    }

    // unknown 数据直接落库
    if payload.marking.as_deref() == Some("unknown") {
        let event_model = event_from_payload(&payload, &task);
        let _ = event_svc::create_event(&app_state.db, event_model).await;
        push_to_kafka_filtered(app_state.clone(), &payload).await;
        let _: Result<(), _> = redis::cmd("SET")
            .arg(&engin_event_id)
            .arg(&engin_event_id)
            .arg("EX")
            .take()
            .arg(600)
            .query(&mut redis_conn);

        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: unknown event saved",
            logging_engine_event_id
        ))));
    };

    // 获取event_type 信息
    let Some(event_type) = payload.event_type.as_deref() else {
        return Ok(Json(RespResult::ok_with_msg(format!(
            "盒子上报信息: engin_event_id: {}, RESULT: 校验异常：Invalid event_type",
            logging_engine_event_id
        ))));
    };

    // 人审判断
    let review_status = match algorithm_svc::find_algorithm_by_code(
        &app_state.db,
        &app_state.redis_client,
        event_type,
    )
    .await
    {
        Ok(Some(algorithm)) => {
            event_processing_svc::personnel_check(&payload, &algorithm, &mut redis_conn)
        }
        _ => {
            return Ok(Json(RespResult::ok_with_msg(format!(
                "盒子上报信息: engin_event_id: {}, RESULT: No algorithm found for event type. Skipping personnel check",
                logging_engine_event_id
            ))));
        }
    };
    // 执行人审判断判断
    let mut event_model: event::Model = event_from_payload(&payload, &task);
    let mut should_push_to_dq = false; // Flag to determine if we should call push_event_to_dq

    if review_status == event_processing_svc::PersonnelCheckResult::Enable {
        event_model.marking = Some("init".to_string());
        event_model.marking_time = Some(Local::now().naive_local());
        should_push_to_dq = true;
    } else {
        let mut extra = event_model.extra.unwrap_or(json!({}));
        let marking =
            json!({"MarkEventCount": 1, "MarkingBy": 0, "MarkingTime": Local::now().naive_local()});
        extra["marking"] = marking;
        event_model.extra = Some(extra);
        event_model.marking = Some("event".to_string());
    };
    // 执行入库
    let saved_event = event_svc::create_event(&app_state.db, event_model)
        .await
        .map_err(|err| anyhow!(err))?;

    // If personnel check was enabled, now call push_event_to_dq with saved_event.id
    if should_push_to_dq {
        push_event_to_dq(app_state.clone(), &payload, &task, saved_event.id).await;
    }

    // 事件engin_event_id到Redis 防止重放
    let _: Result<(), _> = redis::cmd("SET")
        .arg(&payload.engine_event_id)
        .arg(&payload.engine_event_id)
        .arg("EX")
        .arg(600)
        .query(&mut redis_conn);

    return Ok(Json(RespResult::ok_with_msg(format!(
        "盒子上报信息: engin_event_id: {}, RESULT：Event processed successfully",
        logging_engine_event_id
    ))));
}

async fn push_event_to_dq(
    app_state: Arc<AppState>,
    payload: &BoxReportRequest,
    _task: &task::Model, // Renamed to _task
    event_id: i64,
) {
    tracing::info!("Starting push_event_to_dq for event_id: {}", event_id);
    // Initialize reviewDataVo
    let mut review_data_vo = ReviewDataPushVo {
        id: event_id,
        // Copy fields from payload
        task_code: payload.task_code.clone(),
        source: payload.source.clone(),
        event_type: payload.event_type.clone(),
        event_time: payload.event_time,
        end_time: payload.end_time,
        marking: payload.marking.clone(),
        engine_event_id: payload.engine_event_id.clone(),
        vehicle_type: payload.vehicle_type.clone(),
        plate_number: payload.plate_number.clone(),
        plate_color: payload.plate_color.clone(),
        special_car_type: payload.special_car_type.clone(),
        engine_version: payload.engine_version.clone(),
        snapshot: payload.snapshot.clone(),
        snapshot_uri_compress: payload.snapshot_uri_compress.clone(),
        snapshot_uri_raw_compress: payload.snapshot_uri_raw_compress.clone(),
        snapshot_uri_cover_compress: payload.snapshot_uri_cover_compress.clone(),
        extra_data: payload.extra_data.clone(),
        camera_code: payload.camera_code.clone(),
        evidence_status: payload.evidence_status.clone(),
        evidence_url: payload.evidence_url.clone(),
        original_violation_index: payload.original_violation_index,
        extra: payload.extra.clone(),
        project_id: payload.project_id,
        project_name: payload.project_name.clone(),
        company_id: payload.company_id,
        company_name: payload.company_name.clone(),

        // Initialize other fields to None
        position: None,
        task_snapshot: None,
        original_config: None,
        editable: None,
    };

    // Process extraData
    if let Some(origin_data) = payload.extra_data.clone() {
        // position
        if let Some(position_array) = origin_data.get("position").and_then(|v| v.as_array()) {
            if !position_array.is_empty() {
                review_data_vo.position = Some(Value::Array(position_array.clone()));
            }
        }

        // snapshot and taskSnapshot
        review_data_vo.snapshot = payload.snapshot.clone();
        if let Some(task_snapshot_str) = origin_data.get("taskSnapshot").and_then(|v| v.as_str()) {
            review_data_vo.task_snapshot = Some(task_snapshot_str.to_string());
        }

        // originalConfig with violations and drawType
        let mut original_config_json = json!({});
        if let (Some(event_type), Some(original_config_from_extra)) =
            (&payload.event_type, origin_data.get("originalConfig"))
        {
            if let Some(alg_list) = original_config_from_extra
                .get("algList")
                .and_then(|v| v.as_array())
            {
                let event_algs: Vec<&Value> = alg_list
                    .iter()
                    .filter(|item| {
                        item.get("eventType")
                            .and_then(|v| v.as_str())
                            .map_or(false, |et| et == event_type)
                    })
                    .collect();

                if let Some(event_alg) = event_algs.first() {
                    if let Some(alg_param) = event_alg.get("algParam") {
                        let mut violations_array = Vec::new();
                        violations_array.push(alg_param.clone());
                        original_config_json["violations"] = Value::Array(violations_array);
                    }
                }
            }
        }

        // Fetch algorithm for drawType and editable
        if let Some(event_type) = payload.event_type.as_deref() {
            // Note: The Java code uses project_id + event_type as a key for caching algorithms.
            // Our algorithm_svc::find_algorithm_by_code currently takes only the code (event_type).
            // This might be a simplification or a difference in cache key strategy.
            // For now, we proceed with the existing `find_algorithm_by_code` signature.
            if let Ok(Some(algorithm)) = algorithm_svc::find_algorithm_by_code(
                &app_state.db,
                &app_state.redis_client,
                event_type,
            )
            .await
            {
                if let Some(draw_type) = algorithm.draw_type {
                    original_config_json["drawType"] = json!(draw_type);
                }

                if let Some(editable_config_value) = algorithm.editable_config {
                    // Assuming editable_config_value is already a serde_json::Value
                    if let Some(config_array) = editable_config_value
                        .get("config")
                        .and_then(|v| v.as_array())
                    {
                        review_data_vo.editable = Some(Value::Array(config_array.clone()));
                    }
                }
            }
        }
        review_data_vo.original_config = Some(original_config_json);
    }

    // Send to DQ service
    let dq_url = format!("{}/v1/dq-service/event/add", app_state.dq_service_url);

    // Serialize the review_data_vo to JSON
    let json_payload = match serde_json::to_value(&review_data_vo) {
        Ok(json) => json,
        Err(e) => {
            tracing::error!("Failed to serialize ReviewDataPushVo to JSON: {}", e);
            return;
        }
    };
    tracing::info!("Sending eventDto: {} to DQ service", json_payload);
    // Create a reqwest client
    let client = reqwest::Client::new();

    // Send the POST request
    match client.post(&dq_url).json(&json_payload).send().await {
        Ok(response) => {
            if response.status().is_success() {
                tracing::info!(
                    "Successfully pushed event to DQ service for event ID: {}",
                    event_id
                );
            } else {
                let status = response.status();
                let body = response.text().await.unwrap_or_default();
                tracing::error!(
                    "Failed to push event to DQ service. Status: {}, Body: {}",
                    status,
                    body
                );
            }
        }
        Err(e) => {
            tracing::error!(
                "Failed to send HTTP request to DQ service for event ID {}: {}",
                event_id,
                e
            );
        }
    }
}

// --- Helper Functions ---

/// Fetches task data and enriches the payload, mirroring `deposeBaseEventTaskData`.
async fn enrich_payload_with_task_data(
    app_state: &Arc<AppState>,
    payload: &mut BoxReportRequest,
) -> Result<task::Model> {
    
    let Some(task_code) = payload.task_code.as_deref() else {
        tracing::error!("Task code is missing in the payload.");
        return Err(anyhow!("Task code is missing in the payload."));
    };

    let task = task_svc::find_running_tasks_by_criteria(
        &app_state.db,
        &app_state.redis_client,
        task_svc::TaskSearchCriteria {
            code: Some(task_code),
            ..Default::default()
        },
    )
    .await;

    match task {
        Ok(tasks) if !tasks.is_empty() => {
            let task = tasks.into_iter().next().unwrap();
            Ok(task)
        }
        _ => Err(anyhow!("获取task {:?} 缓存信息失败", task_code)), // Return concrete error type
    }
}

/// Implements the event type cooling-down filter.
async fn handle_cooling_down_filter(
    app_state: &Arc<AppState>,
    vo: &BoxReportRequest,
    redis_conn: &mut impl Commands,
) -> bool {
    // This config is global in Java, so we get it from the app state.
    // Assuming `app_state.filter_events_config` holds `{ "is_open": true, "event_types": "7021,7022" }`
    if !app_state
        .filter_events_config
        .get("is_open")
        .and_then(|v| v.as_bool())
        .unwrap_or(false)
    {
        return false;
    }

    let event_types_str = app_state
        .filter_events_config
        .get("event_types")
        .and_then(|v| v.as_str())
        .unwrap_or("");
    let Some(current_event_type) = vo.event_type.as_deref() else {
        return false;
    };
    if !event_types_str
        .split(',')
        .any(|et| et == current_event_type)
    {
        return false;
    }
    let Some(cooling_second) = vo
        .extra_data
        .as_ref()
        .and_then(|ed| ed.get("originalConfig"))
        .and_then(|oc| oc.get("algList"))
        .and_then(|al| al.as_array())
        .and_then(|alg_list| alg_list.get(vo.original_violation_index.unwrap_or(0) as usize))
        .and_then(|alg_item| alg_item.get("algParam"))
        .and_then(|ap| ap.get("cooling_second"))
        .and_then(|cs| cs.as_i64())
    else {
        return false;
    };
    if cooling_second <= 0 {
        return false;
    }

    let redis_key = format!(
        "FILTER_EVENT_TYPE:_{}_{}",
        vo.task_code.as_deref().unwrap_or(""),
        current_event_type
    );
    if let Ok(value_str) = redis_conn.get::<_, String>(&redis_key) {
        let parts: Vec<&str> = value_str.split('@').collect();
        if parts.len() == 2 {
            let old_cooling = parts[0].parse::<i64>().unwrap_or(0);
            let old_timestamp_ms = parts[1].parse::<i64>().unwrap_or(0);
            if old_cooling == cooling_second {
                return true;
            }

            let old_event_time_dt_utc = Utc.timestamp_millis_opt(old_timestamp_ms).single();

            if let (Some(event_time), Some(dt_utc)) = (vo.event_time, old_event_time_dt_utc) {
                let old_event_time_naive = dt_utc.naive_utc();
                if event_time.naive_utc()
                    <= (old_event_time_naive + Duration::seconds(cooling_second))
                {
                    return true;
                }
            }
        }
    }

    if let Some(event_time) = vo.event_time {
        let expiry_seconds = ((event_time + Duration::seconds(cooling_second)) - Utc::now())
            .num_seconds()
            .max(1); // Ensure min 1 second TTL
        let new_value = format!("{}@{}", cooling_second, event_time.timestamp_millis());
        let _: Result<(), _> = redis_conn.set_ex(redis_key, new_value, expiry_seconds as u64);
    }

    false
}

/// Simulates pushing the event to a "filtered" Kafka topic.
async fn push_to_kafka_filtered(app_state: Arc<AppState>, payload: &BoxReportRequest) {
    if payload.marking.as_deref() == Some("filtered") {
        let topic = "PLATFORM_CUSTOMER_META_EVENTS_FILTERED";
        let url = format!(
            "{}/v1/message/center/mq/produce/topic/{}",
            app_state.message_center_url, topic
        );

        // Serialize the payload to JSON
        let Ok(json_payload) = serde_json::to_value(payload) else {
            tracing::error!("Failed to serialize payload to JSON");
            return;
        };

        // Create a reqwest client
        let client = reqwest::Client::new();

        // Send the POST request
        let Ok(response) = client.post(&url).json(&json_payload).send().await else {
            tracing::error!(
                "Failed to send HTTP request to Message Center API for Kafka topic '{}', reviewData.eventId:{}",
                topic,
                payload.id.unwrap_or_default()
            );
            return;
        };
        if response.status().is_success() {
            tracing::info!(
                "Successfully pushed event to Kafka topic '{}' via Message Center API for event ID: {}",
                topic,
                payload.engine_event_id.as_deref().unwrap_or("")
            );
        } else {
            let status = response.status();
            let body = response.text().await.unwrap_or_default();
            tracing::error!(
                "Failed to push event to Kafka topic '{}' via Message Center API. Status: {}, Body: {}",
                topic,
                status,
                body
            );
        };
    }
}

/// Maps the final request payload and task data to the database event model.
fn event_from_payload(payload: &BoxReportRequest, task: &task::Model) -> event::Model {
    let generated_id = Local::now().timestamp_millis();
    event::Model {
        id: payload.id.unwrap_or(generated_id),
        project_id: Some(payload.project_id),
        project_name: Some(payload.project_name.clone()),
        task_code: payload.task_code.clone(),
        task_name: task.name.clone(),
        scene_id: task.scene_id,
        source: payload.source.clone(),
        event_type: payload.event_type.clone(),
        event_type_name: payload.event_type_name.clone(),
        event_time: payload.event_time.map(|dt| dt.naive_local()),
        end_time: payload.end_time.map(|dt| dt.naive_local()),
        marking: payload.marking.clone(),
        engine_event_id: payload.engine_event_id.clone(),
        vehicle_type: payload.vehicle_type.clone(),
        plate_number: payload.plate_number.clone(),
        plate_color: payload.plate_color.clone(),
        special_car_type: payload.special_car_type.clone(),
        engine_version: payload.engine_version.clone(),
        snapshot: payload.snapshot.clone(),
        snapshot_uri_compress: payload.snapshot_uri_compress.clone(),
        snapshot_uri_raw_compress: payload.snapshot_uri_raw_compress.clone(),
        snapshot_uri_cover_compress: payload.snapshot_uri_cover_compress.clone(),
        extra_data: payload.extra_data.clone(),
        camera_code: payload.camera_code.clone(),
        evidence_status: payload.evidence_status.clone(),
        evidence_url: payload.evidence_url.clone(),
        original_violation_index: payload.original_violation_index,
        extra: payload.extra.clone(),
        marking_time: if payload.marking.as_deref() == Some("event") {
            Some(Local::now().naive_local())
        } else {
            None
        },
        discard_id: None,
        car_in_event: None,
        filtered_type: None,
        marking_count: None,
        company_id: None,
        company_name: None,
        create_time: Local::now().naive_local(),
        update_time: Local::now().naive_local(),
        create_by: Some(0),
        update_by: Some(0),
        is_del: 0,
    }
}
