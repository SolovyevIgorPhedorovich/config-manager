package com.project.configmanager.audit.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_log")
public class EventLogEntity extends BaseEvent {
}