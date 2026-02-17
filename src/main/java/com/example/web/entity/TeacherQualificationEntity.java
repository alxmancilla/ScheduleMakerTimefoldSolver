package com.example.web.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "teacher_qualification")
@IdClass(TeacherQualificationEntity.TeacherQualificationId.class)
public class TeacherQualificationEntity {

    @Id
    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Id
    @Column(name = "qualification", length = 200)
    private String qualification;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", insertable = false, updatable = false)
    @JsonBackReference("teacher-qualifications")
    private TeacherEntity teacher;

    public TeacherQualificationEntity() {
    }

    public TeacherQualificationEntity(String teacherId, String qualification) {
        this.teacherId = teacherId;
        this.qualification = qualification;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public TeacherEntity getTeacher() {
        return teacher;
    }

    public void setTeacher(TeacherEntity teacher) {
        this.teacher = teacher;
        if (teacher != null) {
            this.teacherId = teacher.getId();
        }
    }

    // Composite Key Class
    public static class TeacherQualificationId implements Serializable {
        private String teacherId;
        private String qualification;

        public TeacherQualificationId() {
        }

        public TeacherQualificationId(String teacherId, String qualification) {
            this.teacherId = teacherId;
            this.qualification = qualification;
        }

        public String getTeacherId() {
            return teacherId;
        }

        public void setTeacherId(String teacherId) {
            this.teacherId = teacherId;
        }

        public String getQualification() {
            return qualification;
        }

        public void setQualification(String qualification) {
            this.qualification = qualification;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TeacherQualificationId that = (TeacherQualificationId) o;
            return teacherId.equals(that.teacherId) && qualification.equals(that.qualification);
        }

        @Override
        public int hashCode() {
            return teacherId.hashCode() + qualification.hashCode();
        }
    }
}
