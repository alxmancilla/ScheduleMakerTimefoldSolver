package com.example.web.controller;

import com.example.web.dto.ScheduleViewDTO;
import com.example.web.entity.*;
import com.example.web.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

        @Autowired
        private CourseBlockAssignmentRepository assignmentRepository;

        @Autowired
        private BlockTimeslotRepository timeslotRepository;

        @Autowired
        private CourseRepository courseRepository;

        @Autowired
        private TeacherRepository teacherRepository;

        @Autowired
        private RoomRepository roomRepository;

        @Autowired
        private StudentGroupRepository groupRepository;

        @GetMapping("/view")
        public ScheduleViewDTO getScheduleView() {
                List<CourseBlockAssignmentEntity> assignments = assignmentRepository.findAssignedBlocks();
                Map<String, BlockTimeslotEntity> timeslots = timeslotRepository.findAll().stream()
                                .collect(Collectors.toMap(BlockTimeslotEntity::getId, t -> t));
                Map<String, CourseEntity> courses = courseRepository.findAll().stream()
                                .collect(Collectors.toMap(CourseEntity::getId, c -> c));
                Map<String, TeacherEntity> teachers = teacherRepository.findAll().stream()
                                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));
                Map<String, RoomEntity> rooms = roomRepository.findAll().stream()
                                .collect(Collectors.toMap(RoomEntity::getName, r -> r));
                Map<String, StudentGroupEntity> groups = groupRepository.findAll().stream()
                                .collect(Collectors.toMap(StudentGroupEntity::getId, g -> g));

                List<ScheduleViewDTO.ScheduleEntry> entries = new ArrayList<>();

                for (CourseBlockAssignmentEntity assignment : assignments) {
                        BlockTimeslotEntity timeslot = timeslots.get(assignment.getBlockTimeslotId());
                        CourseEntity course = courses.get(assignment.getCourseId());
                        TeacherEntity teacher = teachers.get(assignment.getTeacherId());
                        RoomEntity room = rooms.get(assignment.getRoomName());
                        StudentGroupEntity group = groups.get(assignment.getGroupId());

                        if (timeslot != null && course != null) {
                                ScheduleViewDTO.ScheduleEntry entry = new ScheduleViewDTO.ScheduleEntry();
                                entry.setId(assignment.getId());
                                entry.setDayOfWeek(timeslot.getDayOfWeek());
                                entry.setStartHour(timeslot.getStartHour());
                                entry.setLengthHours(timeslot.getLengthHours());
                                entry.setCourseName(course.getName());
                                entry.setTeacherName(teacher != null ? teacher.getName() + " " + teacher.getLastName()
                                                : null);
                                entry.setRoomName(room != null ? room.getName() : null);
                                entry.setGroupName(group != null ? group.getName() : null);
                                entry.setPinned(assignment.getPinned());

                                entries.add(entry);
                        }
                }

                ScheduleViewDTO view = new ScheduleViewDTO();
                view.setEntries(entries);
                view.setTotalAssignments(assignments.size());
                view.setAssignedCount(assignments.size());
                view.setUnassignedCount(assignmentRepository.findUnassignedBlocks().size());

                return view;
        }

        @GetMapping("/view/group/{groupId}")
        public ScheduleViewDTO getScheduleViewByGroup(@PathVariable String groupId) {
                List<CourseBlockAssignmentEntity> assignments = assignmentRepository.findByGroupId(groupId)
                                .stream()
                                .filter(a -> a.getBlockTimeslotId() != null)
                                .collect(Collectors.toList());

                return buildScheduleView(assignments);
        }

        @GetMapping("/view/teacher/{teacherId}")
        public ScheduleViewDTO getScheduleViewByTeacher(@PathVariable String teacherId) {
                List<CourseBlockAssignmentEntity> assignments = assignmentRepository.findByTeacherId(teacherId)
                                .stream()
                                .filter(a -> a.getBlockTimeslotId() != null)
                                .collect(Collectors.toList());

                return buildScheduleView(assignments);
        }

        @GetMapping("/view/room/{roomName}")
        public ScheduleViewDTO getScheduleViewByRoom(@PathVariable String roomName) {
                List<CourseBlockAssignmentEntity> assignments = assignmentRepository.findByRoomName(roomName)
                                .stream()
                                .filter(a -> a.getBlockTimeslotId() != null)
                                .collect(Collectors.toList());

                return buildScheduleView(assignments);
        }

        private ScheduleViewDTO buildScheduleView(List<CourseBlockAssignmentEntity> assignments) {
                Map<String, BlockTimeslotEntity> timeslots = timeslotRepository.findAll().stream()
                                .collect(Collectors.toMap(BlockTimeslotEntity::getId, t -> t));
                Map<String, CourseEntity> courses = courseRepository.findAll().stream()
                                .collect(Collectors.toMap(CourseEntity::getId, c -> c));
                Map<String, TeacherEntity> teachers = teacherRepository.findAll().stream()
                                .collect(Collectors.toMap(TeacherEntity::getId, t -> t));
                Map<String, RoomEntity> rooms = roomRepository.findAll().stream()
                                .collect(Collectors.toMap(RoomEntity::getName, r -> r));
                Map<String, StudentGroupEntity> groups = groupRepository.findAll().stream()
                                .collect(Collectors.toMap(StudentGroupEntity::getId, g -> g));

                List<ScheduleViewDTO.ScheduleEntry> entries = new ArrayList<>();

                for (CourseBlockAssignmentEntity assignment : assignments) {
                        BlockTimeslotEntity timeslot = timeslots.get(assignment.getBlockTimeslotId());
                        CourseEntity course = courses.get(assignment.getCourseId());
                        TeacherEntity teacher = teachers.get(assignment.getTeacherId());
                        RoomEntity room = rooms.get(assignment.getRoomName());
                        StudentGroupEntity group = groups.get(assignment.getGroupId());

                        if (timeslot != null && course != null) {
                                ScheduleViewDTO.ScheduleEntry entry = new ScheduleViewDTO.ScheduleEntry();
                                entry.setId(assignment.getId());
                                entry.setDayOfWeek(timeslot.getDayOfWeek());
                                entry.setStartHour(timeslot.getStartHour());
                                entry.setLengthHours(timeslot.getLengthHours());
                                entry.setCourseName(course.getName());
                                entry.setTeacherName(teacher != null ? teacher.getName() + " " + teacher.getLastName()
                                                : null);
                                entry.setRoomName(room != null ? room.getName() : null);
                                entry.setGroupName(group != null ? group.getName() : null);
                                entry.setPinned(assignment.getPinned());

                                entries.add(entry);
                        }
                }

                ScheduleViewDTO view = new ScheduleViewDTO();
                view.setEntries(entries);
                view.setTotalAssignments(assignments.size());
                view.setAssignedCount(assignments.size());

                return view;
        }
}
