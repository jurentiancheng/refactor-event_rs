//! Other event filtering logic (e.g., based on position).

use redis::Commands;
use serde_json::{Value, json};

use crate::ctl::box_report_ctl::BoxReportRequest;

/// The main filter function for other events.
/// Returns `(true, reason)` if the event should be filtered.
pub fn filter(
    vo: &mut BoxReportRequest,
    other_json_obj: &Value,
    redis_conn: &mut impl Commands,
) -> (bool, Option<&'static str>) {
    if ignore_same_pos_events(vo, other_json_obj, redis_conn) {
        return (true, Some("samePosition"));
    }
    if ignore_all_events(vo, other_json_obj) {
        return (true, Some("ignoreAllEvents"));
    }
    if ignore_part_events(vo, other_json_obj) {
        return (true, Some("ignorePartEvents"));
    }
    (false, None)
}

fn ignore_all_events(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("ignoreAllEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };
    
    event_types.iter().any(|v| v.as_str() == Some(event_type))
}

fn ignore_part_events(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("ignorePartEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(expected_result) = config.get("eventResult").and_then(|v| v.as_str()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };

    if !event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return false;
    }
    
    let Some(actual_result) = vo.extra_data.as_ref()
        .and_then(|e| e.get("eventResult"))
        .and_then(|er| er.get("result"))
        .and_then(|r| r.as_str()) else { return false };

    actual_result == expected_result
}


fn ignore_same_pos_events(
    vo: &BoxReportRequest,
    config: &Value,
    redis_conn: &mut impl Commands,
) -> bool {
    let Some(config) = config.get("ignoreSamePosEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };
    if !event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return false;
    }

    let Some(cooling_seconds) = config.get("coolingSeconds").and_then(|v| v.as_u64()) else { return false };
    let Some(pos_overlap_percent) = config.get("posOverlapPercent").and_then(|v| v.as_f64()) else { return false };
    
    let Some(extra_data) = vo.extra_data.as_ref() else { return false };

    if let Some(position) = extra_data.get("position").and_then(|p| p.as_array()) {
        // Flow events logic
        ignore_same_pos_flow_events(vo, cooling_seconds, pos_overlap_percent, position, redis_conn)
    } else {
        // Other events logic (using snapshot)
        ignore_same_pos_other_events(vo, cooling_seconds, pos_overlap_percent, redis_conn)
    }
}

fn ignore_same_pos_flow_events(
    vo: &BoxReportRequest,
    cooling_seconds: u64,
    pos_overlap_percent: f64,
    position: &Vec<Value>,
    redis_conn: &mut impl Commands,
) -> bool {
    if position.len() < 4 { return false; }
    let key = format!(
        "POS_KEY:{}:{}:{}",
        vo.task_code.as_deref().unwrap_or(""), // Assuming projectId is not yet available, use task_code
        vo.event_type.as_deref().unwrap_or(""),
        "" // No specific identifier for flow events other than task/event type
    );

    let base_position_str: Result<String, _> = redis_conn.get(&key);
    
    if let Ok(base_pos_str) = base_position_str {
        if let Ok(base_pos) = serde_json::from_str::<Vec<f64>>(&base_pos_str) {
             if base_pos.len() < 4 {
                let _: Result<(), _> = redis_conn.set_ex(&key, json!(position).to_string(), cooling_seconds);
                return false;
             }
            let rate = calculate_flow_rate(
                &position.iter().map(|v| v.as_f64().unwrap_or(0.0)).collect::<Vec<_>>(),
                &base_pos
            );
            if rate > pos_overlap_percent {
                return true; // Filter
            }
        }
    }
    
    let _: Result<(), _> = redis_conn.set_ex(&key, json!(position).to_string(), cooling_seconds);
    false
}

fn calculate_flow_rate(pos1: &[f64], pos2: &[f64]) -> f64 {
    let (x1, y1, x2, y2) = (pos1[0], pos1[1], pos1[2], pos1[3]);
    let (bx1, by1, bx2, by2) = (pos2[0], pos2[1], pos2[2], pos2[3]);
    
    let inner_x1 = x1.max(bx1);
    let inner_y1 = y1.max(by1);
    let inner_x2 = x2.min(bx2);
    let inner_y2 = y2.min(by2);

    let inner_area = (inner_x2 - inner_x1).max(0.0) * (inner_y2 - inner_y1).max(0.0);
    let area1 = (x2 - x1) * (y2 - y1);
    let area2 = (bx2 - bx1) * (by2 - by1);

    if (area1 + area2 - inner_area) == 0.0 { return 0.0; }
    inner_area / (area1 + area2 - inner_area)
}


fn ignore_same_pos_other_events(
    vo: &BoxReportRequest,
    cooling_seconds: u64,
    pos_overlap_percent: f64,
    redis_conn: &mut impl Commands,
) -> bool {
    let Some(snapshot) = vo.snapshot.as_ref().and_then(|s| s.as_array()) else { return false };
    if snapshot.is_empty() { return false; }
    let Some(pts) = snapshot[0].get("pts").and_then(|p| p.as_array()) else { return false };
    if !is_right_pts(pts) { return false; }
    
    let key = format!(
        "POS_KEY:{}:{}:{}",
        vo.task_code.as_deref().unwrap_or(""),
        vo.event_type.as_deref().unwrap_or(""),
        ""
    );
    
    let base_pts_str: Result<String, _> = redis_conn.get(&key);
    
    if let Ok(base_pts_str) = base_pts_str {
        if let Ok(base_pts) = serde_json::from_str::<Vec<Vec<f64>>>(&base_pts_str) {
            if !is_right_pts_f64(&base_pts) {
                let _: Result<(), _> = redis_conn.set_ex(&key, json!(pts).to_string(), cooling_seconds);
                return false;
            }
            let pts_f64 = pts.iter().map(|p| {
                p.as_array().unwrap().iter().map(|v| v.as_f64().unwrap_or(0.0)).collect::<Vec<_>>()
            }).collect::<Vec<_>>();

            let rate = calculate_other_rate(&pts_f64, &base_pts);
            if rate > pos_overlap_percent {
                return true; // Filter
            }
        }
    }

    let _: Result<(), _> = redis_conn.set_ex(&key, json!(pts).to_string(), cooling_seconds);
    false
}

fn calculate_other_rate(pts1: &[Vec<f64>], pts2: &[Vec<f64>]) -> f64 {
    let (x1, y1) = (pts1[0][0], pts1[0][1]);
    let (x2, y2) = (pts1[1][0], pts1[1][1]);
    let (bx1, by1) = (pts2[0][0], pts2[0][1]);
    let (bx2, by2) = (pts2[1][0], pts2[1][1]);

    let inner_x1 = x1.max(bx1);
    let inner_y1 = y1.max(by1);
    let inner_x2 = x2.min(bx2);
    let inner_y2 = y2.min(by2);

    let inner_area = (inner_x2 - inner_x1).max(0.0) * (inner_y2 - inner_y1).max(0.0);
    let area1 = (x2 - x1) * (y2 - y1);
    let area2 = (bx2 - bx1) * (by2 - by1);

    if (area1 + area2 - inner_area) == 0.0 { return 0.0; }
    inner_area / (area1 + area2 - inner_area)
}


fn is_right_pts(pts: &[Value]) -> bool {
    if pts.len() < 2 { return false; }
    let Some(pts1) = pts[0].as_array() else { return false; };
    if pts1.len() < 2 { return false; }
    let Some(pts2) = pts[1].as_array() else { return false; };
    if pts2.len() < 2 { return false; }
    true
}

fn is_right_pts_f64(pts: &[Vec<f64>]) -> bool {
    if pts.len() < 2 { return false; }
    if pts[0].len() < 2 { return false; }
    if pts[1].len() < 2 { return false; }
    true
}
