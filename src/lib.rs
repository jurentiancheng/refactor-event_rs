use axum::Json;
use serde_json::Value;

use crate::{exception::AppError, response_result::RespResult};

pub type JsonResponse<T> = Result<Json<RespResult<T>>, AppError>;
pub trait IntoJsonValue {
    fn into_json_with_snake_key(&self) -> Value;
}

pub mod common_func {
    /**
     * CamelCase to UnderScore
     */
    pub fn camel_case_to_under_score(camel_case: &str) -> String {
        camel_case
            .chars()
            .enumerate()
            .map(|(i, c)| {
                if c.is_uppercase() && i > 0 {
                    format!("_{}", c.to_ascii_lowercase())
                } else {
                    c.to_string()
                }
            })
            .collect::<String>()
    }
}
pub mod exception {

    use super::response_result::RespResult;
    use axum::{Json, http::StatusCode, response::IntoResponse};

    pub struct AppError(anyhow::Error);

    // Tell Axum how to convert `AppError` into a response.
    impl IntoResponse for AppError {
        fn into_response(self) -> axum::response::Response {
            let error_message = format!("{}", self.0); // Use display format for user-facing messages
            let mut status_code = StatusCode::INTERNAL_SERVER_ERROR;
            let mut resp_result_error = RespResult::<()>::sys_error(error_message.clone()); // Default to sys_error

            // Customize status code and RespResult variant based on error content
            if error_message.starts_with("Bail: ") {
                status_code = StatusCode::OK;
                resp_result_error = RespResult::<()>::business_error(
                    error_message
                        .strip_prefix("Bail: ")
                        .unwrap_or(&error_message)
                        .to_string(),
                );
            }

            tracing::error!("Error: {:?}", self.0); // Log the full error with debug format

            (status_code, Json(resp_result_error)).into_response()
        }
    }

    // This enables using `?` on functions that return `anyhow::Result`
    // to automatically convert them into `AppError`.
    impl<E> From<E> for AppError
    where
        E: Into<anyhow::Error>,
    {
        fn from(err: E) -> Self {
            Self(err.into())
        }
    }

    pub fn internal_err<E>(err: E) -> (StatusCode, Json<RespResult<String>>)
    where
        E: std::error::Error,
    {
        (
            StatusCode::INTERNAL_SERVER_ERROR,
            Json(RespResult::sys_error(err.to_string())),
        )
    }

    pub fn validation_err(err: String) -> (StatusCode, Json<RespResult<String>>) {
        (StatusCode::OK, Json(RespResult::validation_error(err)))
    }

    pub fn business_error(err: String) -> (StatusCode, Json<RespResult<String>>) {
        (StatusCode::OK, Json(RespResult::business_error(err)))
    }
}

pub mod response_result {
    use std::any::Any;

    use serde::Serialize;

    #[derive(Serialize, Debug, Default)]
    pub struct RespResult<T> {
        pub code: i32,
        pub data: Option<T>,
        pub message: String,
    }

    impl<T> RespResult<T>
    where
        T: Any,
    {
        pub fn ok(data: T) -> Self {
            Self {
                code: 0,
                message: "ok".to_string(),
                data: Some(data),
            }
        }

        pub fn ok_with_msg(msg: String) -> Self {
            Self {
                code: 0,
                message: msg,
                data: None,
            }
        }
        pub fn sys_error(msg: String) -> Self {
            Self {
                code: 500,
                message: msg,
                data: None,
            }
        }
        pub fn validation_error(msg: String) -> Self {
            Self {
                code: 6001,
                message: msg,
                data: None,
            }
        }
        pub fn business_error(msg: String) -> Self {
            Self {
                code: 7002,
                message: msg,
                data: None,
            }
        }
    }
}

pub mod pagination {
    use serde::Serialize;

    #[derive(Debug, Serialize, Default)]
    pub struct PageInfo {
        page: u64,
        size: u64,
        total: u64,
    }

    impl PageInfo {
        pub fn from(page: u64, size: u64, total: u64) -> Self {
            Self { page, size, total }
        }
    }

    #[derive(Debug, Default, Serialize)]
    pub struct PageData<T> {
        pub page_info: PageInfo,
        pub page_data: Vec<T>,
    }

    impl<T> PageData<T>
    where
        T: Serialize,
    {
        pub fn new(page_info: PageInfo, page_data: Vec<T>) -> Self {
            Self {
                page_info,
                page_data,
            }
        }
    }

    pub trait Pageable {
        fn get_page(&self) -> Option<u64>;
        fn get_size(&self) -> Option<u64> {
            Some(20)
        }
        fn get_offset(&self) -> Option<u64> {
            let page = match self.get_page() {
                Some(page) => page,
                None => 1,
            };
            let size = match self.get_size() {
                Some(size) => size,
                None => 20,
            };
            return if page == 1 {
                Some(0)
            } else {
                Some((page - 1) * size)
            };
        }
    }
}

mod datetime_format {
    use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
    use serde::{self, Deserialize, Deserializer, Serializer};

    const FORMAT: &str = "%Y-%m-%d %H:%M:%S";

    pub fn serialize<S>(date: &DateTime<Local>, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        let s = date.format(FORMAT).to_string();
        serializer.serialize_str(&s)
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<DateTime<Local>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let s = String::deserialize(deserializer)?;
        let naive = NaiveDateTime::parse_from_str(&s, FORMAT).map_err(serde::de::Error::custom)?;
        Local
            .from_local_datetime(&naive)
            .single()
            .ok_or_else(|| serde::de::Error::custom("invalid or ambiguous local time"))
    }
}

mod datetime_format_option {
    use chrono::{DateTime, Local, NaiveDateTime, TimeZone};
    use serde::{self, Deserialize, Deserializer, Serializer};

    const FORMAT: &str = "%Y-%m-%d %H:%M:%S";

    pub fn serialize<S>(date: &Option<DateTime<Local>>, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        match date {
            Some(d) => serializer.serialize_str(&d.format(FORMAT).to_string()),
            None => serializer.serialize_none(),
        }
    }

    pub fn deserialize<'de, D>(deserializer: D) -> Result<Option<DateTime<Local>>, D::Error>
    where
        D: Deserializer<'de>,
    {
        let opt: Option<String> = Option::deserialize(deserializer)?;
        match opt {
            Some(s) => {
                let naive =
                    NaiveDateTime::parse_from_str(&s, FORMAT).map_err(serde::de::Error::custom)?;
                Local
                    .from_local_datetime(&naive)
                    .single()
                    .map(Some)
                    .ok_or_else(|| serde::de::Error::custom("invalid or ambiguous local time"))
            }
            None => Ok(None),
        }
    }
}
