//! Plate-related event filtering logic.

use redis::Commands;
use serde_json::Value;

use crate::ctl::bs_model::BoxReportRequest;

const PLATE_COLOR_YELLOW: &[&str] = &["s_yellow", "d_yellow"];

/// The main filter function for plate-related events.
/// Returns `(true, reason)` if the event should be filtered.
pub fn filter(
    vo: &mut BoxReportRequest,
    plate_json_obj: &Value,
    redis_conn: &mut impl Commands,
) -> (bool, Option<&'static str>) {
    if !only_yellow_plate(vo, plate_json_obj) {
        return (true, Some("yellowPlate"));
    }
    if ignore_no_plate_events(vo, plate_json_obj) {
        return (true, Some("noPlate"));
    }
    if ignore_blurry_plate_events(vo, plate_json_obj) {
        return (true, Some("blurryPlate"));
    }
    if only_plate_types(vo, plate_json_obj) {
        return (true, Some("plateColorFiltered"));
    }
    if non_motor_plate_types_filter(vo, plate_json_obj) {
        return (true, Some("plateColorFiltered"));
    }
    if plate_special_text_filter(vo, plate_json_obj) {
        return (true, Some("specialPlateFilter"));
    }
    if short_plate_filter(vo, plate_json_obj) {
        return (true, Some("shortPlateFilter"));
    }
    if ignore_same_plate_events(vo, plate_json_obj, redis_conn) {
        return (true, Some("samePlate"));
    }

    (false, None)
}

/// Returns `false` to filter (if not a yellow plate).
fn only_yellow_plate(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("onlyYellowPlate") else { return true };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return true;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return true };
    let Some(event_type) = vo.event_type.as_deref() else { return true };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        let Some(plate_color) = vo.plate_color.as_deref() else { return false };
        return PLATE_COLOR_YELLOW.contains(&plate_color);
    }
    true
}

/// Returns `true` to filter.
fn ignore_no_plate_events(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("ignoreNoPlateEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return vo.plate_number.as_deref().map_or(true, |s| s.is_empty());
    }
    false
}

/// Returns `true` to filter.
fn ignore_blurry_plate_events(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("ignoreBlurryPlateEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(blurry_level) = config.get("blurryLevel").and_then(|v| v.as_f64()) else { return false };
    if blurry_level <= 0.0 { return false; }
    
    let Some(extra_data) = vo.extra_data.as_ref() else { return false };
    let Some(score) = extra_data.get("plateNumberScore").and_then(|v| v.as_f64()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return score < blurry_level;
    }
    false
}

/// Returns `true` to filter.
fn only_plate_types(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("onlyPlateTypes") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(plate_colors) = config.get("plateColor").and_then(|v| v.as_array()) else { return false };
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };

    let Some(plate_color) = vo.plate_color.as_deref() else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return !plate_colors.iter().any(|v| v.as_str() == Some(plate_color));
    }
    false
}

/// Returns `true` to filter.
fn non_motor_plate_types_filter(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(filters) = config.get("nonMotorPlateTypesFilter").and_then(|v| v.as_array()) else { return false };
    let Some(extra_data) = vo.extra_data.as_ref() else { return false };
    let plate_color = extra_data.get("summary")
        .and_then(|s| s.get("plate/type"))
        .and_then(|pt| pt.get("label"))
        .and_then(|l| l.as_str())
        .unwrap_or("nullValue");

    let Some(event_type) = vo.event_type.as_deref() else { return false };

    for filter in filters {
        let Some(plate_colors) = filter.get("plateColor").and_then(|v| v.as_array()) else { continue };
        let Some(event_types) = filter.get("eventTypes").and_then(|v| v.as_array()) else { continue };
        if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
            if !plate_colors.iter().any(|v| v.as_str() == Some(plate_color)) {
                return true;
            }
        }
    }
    false
}

/// Returns `true` to filter.
fn plate_special_text_filter(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("plateSpecialTextFilter") else { return false };
    let Some(special_texts) = config.get("specialTexts").and_then(|v| v.as_array()) else { return false };
    let Some(plate_number) = vo.plate_number.as_deref() else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        for text in special_texts {
            if let Some(text_str) = text.as_str() {
                if plate_number.contains(text_str) {
                    return true;
                }
            }
        }
    }
    false
}

/// Returns `true` to filter.
fn short_plate_filter(vo: &BoxReportRequest, config: &Value) -> bool {
    let Some(config) = config.get("shortPlateFilter") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };

    if event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return vo.plate_number.as_deref().map_or(false, |p| p.len() < 7);
    }
    false
}

/// Returns `true` to filter.
fn ignore_same_plate_events(
    vo: &BoxReportRequest,
    config: &Value,
    redis_conn: &mut impl Commands,
) -> bool {
    let Some(plate_number) = vo.plate_number.as_deref() else { return false };
    let Some(config) = config.get("ignoreSamePlateEvents") else { return false };
    if !config.get("enable").and_then(|v| v.as_bool()).unwrap_or(false) {
        return false;
    }
    let Some(cooling_seconds) = config.get("coolingSeconds").and_then(|v| v.as_u64()) else { return false };
    if cooling_seconds == 0 { return false; }
    
    let Some(event_types) = config.get("eventTypes").and_then(|v| v.as_array()) else { return false };
    let Some(event_type) = vo.event_type.as_deref() else { return false };
    if !event_types.iter().any(|v| v.as_str() == Some(event_type)) {
        return false;
    }
    
    // In the java code, projectId is used, but the DTO doesn't have it directly at this stage.
    // It's added later from the task. We'll assume task_code is unique enough for the key for now.
    let key = format!("PLATE_KEY:{}:{}:{}", vo.task_code.as_deref().unwrap_or(""), event_type, plate_number);

    if let Ok(value) = redis_conn.get::<_, String>(&key) {
        if value == plate_number {
            return true; // Filter
        }
    }

    let _: Result<(), _> = redis_conn.set_ex(key, plate_number, cooling_seconds);
    false
}
