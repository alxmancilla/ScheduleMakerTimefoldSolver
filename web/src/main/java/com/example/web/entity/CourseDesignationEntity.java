package com.example.web.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_designation")
public class CourseDesignationEntity {

    @Id
    private String name;

    public CourseDesignationEntity() {
    }

    public CourseDesignationEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
