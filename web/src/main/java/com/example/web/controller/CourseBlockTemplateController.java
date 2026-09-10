package com.example.web.controller;

import com.example.web.dto.CourseBlockTemplateDTO;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CRUD for a course's custom block templates (course_block_template): an
 * explicit, hand-authored decomposition for one (course, group) pair - or
 * every group taking the course, when groupId is null - instead of letting
 * BlockGenerationService derive blocks generically. Nested under
 * /api/courses/{courseId}/block-templates, same as CourseRoomRequirementController;
 * falls under the general /api/** GET/POST/PUT/DELETE role rules, no
 * ADMIN-only restriction.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/block-templates")
public class CourseBlockTemplateController {

    @Autowired
    private CourseBlockTemplateRepository templateRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private StudentGroupRepository groupRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BlockTimeslotRepository timeslotRepository;

    @GetMapping
    public List<CourseBlockTemplateEntity> getTemplates(@PathVariable String courseId) {
        requireCourse(courseId);
        return templateRepository.findByCourseIdOrderByGroupIdAscBlockIndexAsc(courseId);
    }

    @PostMapping
    public CourseBlockTemplateEntity createTemplate(@PathVariable String courseId,
            @Valid @RequestBody CourseBlockTemplateDTO request) {
        requireCourse(courseId);
        validateReferences(request);
        if (templateRepository.existsByCourseIdAndGroupIdAndBlockIndex(courseId, request.getGroupId(), request.getBlockIndex())) {
            throw new IllegalArgumentException("A template with this group and block index already exists for this course");
        }
        CourseBlockTemplateEntity template = new CourseBlockTemplateEntity(
                courseId, request.getGroupId(), request.getBlockIndex(), request.getBlockLength(),
                request.getRoomType(), request.getPreferredRoomName(), request.getPreferredDay(),
                request.getPinAssignment() != null ? request.getPinAssignment() : Boolean.FALSE,
                request.getPreferredTimeslotId());
        return templateRepository.save(template);
    }

    @PutMapping("/{id}")
    public CourseBlockTemplateEntity updateTemplate(@PathVariable String courseId, @PathVariable Long id,
            @Valid @RequestBody CourseBlockTemplateDTO request) {
        CourseBlockTemplateEntity template = requireTemplate(courseId, id);
        validateReferences(request);
        if (templateRepository.existsByCourseIdAndGroupIdAndBlockIndexAndIdNot(
                courseId, request.getGroupId(), request.getBlockIndex(), id)) {
            throw new IllegalArgumentException("A template with this group and block index already exists for this course");
        }
        template.setGroupId(request.getGroupId());
        template.setBlockIndex(request.getBlockIndex());
        template.setBlockLength(request.getBlockLength());
        template.setRoomType(request.getRoomType());
        template.setPreferredRoomName(request.getPreferredRoomName());
        template.setPreferredDay(request.getPreferredDay());
        template.setPinAssignment(request.getPinAssignment() != null ? request.getPinAssignment() : Boolean.FALSE);
        template.setPreferredTimeslotId(request.getPreferredTimeslotId());
        return templateRepository.save(template);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String courseId, @PathVariable Long id) {
        CourseBlockTemplateEntity template = requireTemplate(courseId, id);
        templateRepository.delete(template);
        return ResponseEntity.noContent().build();
    }

    private void requireCourse(String courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", courseId);
        }
    }

    private CourseBlockTemplateEntity requireTemplate(String courseId, Long id) {
        requireCourse(courseId);
        CourseBlockTemplateEntity template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Block template", String.valueOf(id)));
        if (!template.getCourseId().equals(courseId)) {
            throw new ResourceNotFoundException("Block template", String.valueOf(id));
        }
        return template;
    }

    private void validateReferences(CourseBlockTemplateDTO request) {
        if (request.getGroupId() != null && !groupRepository.existsById(request.getGroupId())) {
            throw new IllegalArgumentException("Group '" + request.getGroupId() + "' does not exist");
        }
        if (request.getPreferredRoomName() != null && !roomRepository.existsById(request.getPreferredRoomName())) {
            throw new IllegalArgumentException("Room '" + request.getPreferredRoomName() + "' does not exist");
        }
        if (request.getPreferredTimeslotId() != null && !timeslotRepository.existsById(request.getPreferredTimeslotId())) {
            throw new IllegalArgumentException("Timeslot '" + request.getPreferredTimeslotId() + "' does not exist");
        }
    }
}
