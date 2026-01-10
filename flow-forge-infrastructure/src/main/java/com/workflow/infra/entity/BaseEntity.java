package com.workflow.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 基础实体类，包含所有表的公共字段。
 * <p>
 * 使用 JPA Auditing 自动管理创建和更新时间。
 */
@MappedSuperclass
public abstract class BaseEntity {

    /**
     * 主键ID，使用 UUID
     */
    protected UUID id;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    protected Instant createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    protected Instant updatedAt;

    /**
     * 删除时间（软删除）
     */
    @Column(name = "deleted_at")
    protected Instant deletedAt;

    /**
     * 获取主键ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * 设置主键ID
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * 获取创建时间
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取创建时间（LocalDateTime）
     */
    public LocalDateTime getCreatedAtAsDateTime() {
        return createdAt != null ? LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault()) : null;
    }

    /**
     * 设置创建时间（LocalDateTime）
     */
    public void setCreatedAtFromDateTime(LocalDateTime dateTime) {
        this.createdAt = dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * 获取更新时间
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取更新时间（LocalDateTime）
     */
    public LocalDateTime getUpdatedAtAsDateTime() {
        return updatedAt != null ? LocalDateTime.ofInstant(updatedAt, ZoneId.systemDefault()) : null;
    }

    /**
     * 设置更新时间（LocalDateTime）
     */
    public void setUpdatedAtFromDateTime(LocalDateTime dateTime) {
        this.updatedAt = dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * 获取删除时间
     */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * 设置删除时间
     */
    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    /**
     * 获取删除时间（LocalDateTime）
     */
    public LocalDateTime getDeletedAtAsDateTime() {
        return deletedAt != null ? LocalDateTime.ofInstant(deletedAt, ZoneId.systemDefault()) : null;
    }

    /**
     * 设置删除时间（LocalDateTime）
     */
    public void setDeletedAtFromDateTime(LocalDateTime dateTime) {
        this.deletedAt = dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    /**
     * 软删除标记
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 标记为已删除
     */
    public void markAsDeleted() {
        this.deletedAt = Instant.now();
    }
}
