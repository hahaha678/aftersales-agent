package com.example.aftersales.aftersales.domain.vo;

/** confirmed=false 时前端展示新版本草稿，必须重新取得用户确认。 */
public record DraftConfirmationVO(boolean confirmed, DraftVO draft, AftersaleVO application, String message) {}
