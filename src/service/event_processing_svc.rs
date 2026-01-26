//! High-level business logic for event processing.

use crate::ctl::bs_model::BoxReportRequest;
use crate::models::{algorithm, event_filter_config};
use crate::service::filters::{other_filter, plate_filter};
use chrono::NaiveTime;
use redis::Commands;
use serde_json::Value;

/// Orchestrates event filtering.
pub fn filter_event(
    vo: &mut BoxReportRequest,
    configs: &[event_filter_config::Model],
    redis_conn: &mut impl Commands,
) -> (bool, Option<&'static str>) {
    // Find plate filter config
    if let Some(plate_config) = get_config(configs, "plate") {
        let (should_filter, reason) = plate_filter::filter(vo, plate_config, redis_conn);
        if should_filter {
            return (true, reason);
        }
    }

    // Find other filter config
    if let Some(other_config) = get_config(configs, "other") {
        let (should_filter, reason) = other_filter::filter(vo, other_config, redis_conn);
        if should_filter {
            return (true, reason);
        }
    }

    (false, None)
}

fn get_config<'a>(
    configs: &'a [event_filter_config::Model],
    group_name: &str,
) -> Option<&'a Value> {
    configs
        .iter()
        .find(|c| c.setting_group.as_deref() == Some(group_name))
        .and_then(|c| c.config.as_ref())
}

#[derive(Debug, PartialEq)]
pub enum PersonnelCheckResult {
    Enable,
    Disable,
}

/// Implements the logic to check if an event should go to manual review.
pub fn personnel_check(
    vo: &BoxReportRequest,
    algorithm: &algorithm::Model,
    redis_conn: &mut impl Commands,
) -> PersonnelCheckResult {
    let Some(extra_data) = vo.extra_data.as_ref() else { return PersonnelCheckResult::Disable };
    let Some(alg_list) = extra_data.get("originalConfig").and_then(|oc| oc.get("algList")).and_then(|al| al.as_array()) else { return PersonnelCheckResult::Disable };
    let original_violation_index = vo.original_violation_index.unwrap_or(0) as usize;
    if original_violation_index >= alg_list.len() {
        return PersonnelCheckResult::Disable;
    }
    let Some(alg_param) = alg_list[original_violation_index].get("algParam") else { return PersonnelCheckResult::Disable };

    // Check for "isOpenDQ" directly in the algorithm parameters
    if let Some(is_open_dq) = alg_param.get("isOpenDQ").and_then(|v| v.as_i64()) {
        if is_open_dq == 0 { // 0 is disable
            return PersonnelCheckResult::Disable;
        }
        // If it's 1 (enable), check time constraints
        return check_dq_time_constraints(vo, alg_param);
    }

    // If not in alg_param, check global Redis key
    let global_review_enabled: bool = redis_conn
        .get::<_, String>("Global_Review")
        .map(|s| s == "1") // Assuming "1" means enabled
        .unwrap_or(false);

    if !global_review_enabled {
        return PersonnelCheckResult::Disable;
    }

    // Check the specific switch in the algorithm model (boxDebugSwitch for box events)
    let Some(box_debug_switch) = algorithm.is_large_model else { return PersonnelCheckResult::Disable }; // Re-using is_large_model as a placeholder for boxDebugSwitch
    if box_debug_switch != 1 { // Assuming 1 is enable
        return PersonnelCheckResult::Disable;
    }

    PersonnelCheckResult::Enable
}

fn check_dq_time_constraints(vo: &BoxReportRequest, alg_param: &Value) -> PersonnelCheckResult {
    let Some(event_time) = vo.event_time else { return PersonnelCheckResult::Disable };
    let Some(open_dq_time) = alg_param.get("openDqTime") else { return PersonnelCheckResult::Enable }; // No time constraint means enabled

    // Check date range
    if let (Some(start_str), Some(end_str)) = (
        open_dq_time.get("openDqStartDate").and_then(|v| v.as_str()),
        open_dq_time.get("openDqEndDate").and_then(|v| v.as_str()),
    ) {
        if let (Ok(start_date), Ok(end_date)) = (
            chrono::NaiveDate::parse_from_str(start_str, "%Y-%m-%d"),
            chrono::NaiveDate::parse_from_str(end_str, "%Y-%m-%d"),
        ) {
            if event_time.date_naive() < start_date || event_time.date_naive() > end_date {
                return PersonnelCheckResult::Disable;
            }
        }
    }

    // Check time range
    let start_time_str = open_dq_time.get("openDqStartTime").and_then(|v| v.as_str()).unwrap_or("00:00");
    let end_time_str = open_dq_time.get("openDqEndTime").and_then(|v| v.as_str()).unwrap_or("23:59");

    if let (Ok(start_time), Ok(end_time)) = (
        NaiveTime::parse_from_str(start_time_str, "%H:%M"),
        NaiveTime::parse_from_str(end_time_str, "%H:%M"),
    ) {
        if event_time.time() < start_time || event_time.time() > end_time {
            return PersonnelCheckResult::Disable;
        }
    }

    PersonnelCheckResult::Enable
}
