
use crate::models::event;
use anyhow::{Context, Result};
use sea_orm::{ActiveModelTrait, DbConn, DbErr, IntoActiveModel, Set};

/// Creates a new event in the database.
pub async fn create_event(db: &DbConn, event_data: event::Model) -> Result<event::Model> {
    let event_json =
        serde_json::to_value(event_data).context("event_data json serialization failed")?;
    let active_model = event::ActiveModel::from_json(event_json)
        .context("event_data json deserialization failed")?;

    let inserted_id_for_log = match &active_model.engine_event_id {
        // Borrow active_model.id
        sea_orm::ActiveValue::Set(engin_id) => (*engin_id).clone(),
        _ => None, // Or handle NotSet/Unchanged if applicable, though for new records it should be Set.
    };

    let inserted_event = active_model
        .insert(db)
        .await
        .map_err(|err| anyhow::anyhow!("Error inserting event with id: {:?}, err:{}",inserted_id_for_log, err))?;
    Ok(inserted_event)
}

/// Updates an existing event by its ID. All fields will be updated.
pub async fn update_event(
    db: &DbConn,
    id: i64,
    event_data: event::Model,
) -> Result<event::Model, DbErr> {
    let mut active_model = event_data.into_active_model();
    active_model.id = Set(id);
    active_model.update(db).await
}
