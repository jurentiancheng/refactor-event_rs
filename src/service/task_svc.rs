use sea_orm::{DbConn, DbErr, EntityTrait, QueryFilter, ColumnTrait};
use redis::{Client, Commands};

use crate::models::{prelude::*, task};

const CACHE_KEY_RUNNING_TASKS: &str = "event_rs::all_running_tasks";
const CACHE_TTL: u64 = 180; // 3 minutes in seconds

/// Fetches all running tasks, using a cache with a 3-minute TTL.
pub async fn get_all_running_tasks(
    db: &DbConn,
    redis_client: &Client,
) -> Result<Vec<task::Model>, DbErr> {
    let mut redis_conn = redis_client.get_connection().map_err(|e| DbErr::Custom(e.to_string()))?;

    // 1. Try to fetch from cache
    if let Ok(cached_tasks) = redis_conn.get::<_, String>(CACHE_KEY_RUNNING_TASKS) {
        if !cached_tasks.is_empty() {
            if let Ok(tasks) = serde_json::from_str(&cached_tasks) {
                return Ok(tasks);
            }
        }
    }

    // 2. If not in cache, query the database for tasks with status "running"
    let tasks = Task::find()
        .filter(task::Column::Status.eq("running"))
        .all(db)
        .await?;

    // 3. Store in cache
    if let Ok(tasks_json) = serde_json::to_string(&tasks) {
        let _: () = redis_conn.set_ex(CACHE_KEY_RUNNING_TASKS, tasks_json, CACHE_TTL).unwrap_or_default();
    }

    Ok(tasks)
}

#[derive(Default)]
pub struct TaskSearchCriteria<'a> {
    pub code: Option<&'a str>,
    pub box_sn: Option<&'a str>,
    pub project_id: Option<i64>,
}

/// Finds running tasks by criteria from the cached full list.
pub async fn find_running_tasks_by_criteria<'a>(
    db: &DbConn,
    redis_client: &Client,
    criteria: TaskSearchCriteria<'a>,
) -> Result<Vec<task::Model>, DbErr> {
    let tasks = get_all_running_tasks(db, redis_client).await?;
    
    let filtered_tasks = tasks.into_iter().filter(|t| {
        if let Some(code) = criteria.code {
            if t.code.as_deref() != Some(code) { return false; }
        }
        if let Some(box_sn) = criteria.box_sn {
            if t.box_sn.as_deref() != Some(box_sn) { return false; }
        }
        if let Some(project_id) = criteria.project_id {
            if t.project_id != Some(project_id) { return false; }
        }
        true
    }).collect();

    Ok(filtered_tasks)
}