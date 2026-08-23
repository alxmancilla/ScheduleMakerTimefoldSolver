package com.example.web.controller;

import com.example.web.dto.ScheduleRunDTO;
import com.example.web.dto.ScheduleViewDTO;
import com.example.web.entity.*;
import com.example.web.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

        @Autowired
        private CourseBlockAssignmentRepository assignmentRepository;

        @Autowired
        private CourseBlockAssignmentCurrentRepository assignmentCurrentRepository;

        @Autowired
        private ScheduleRunRepository scheduleRunRepository;

        @Autowired
        private ScheduleRunResultRepository scheduleRunResultRepository;

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

        @Autowired
        private AppUserRepository appUserRepository;

        /** The retained run history (most recent first), for a run picker - see the `runId` param on the /view* endpoints below. */
        @GetMapping("/runs")
        public List<ScheduleRunDTO> getScheduleRuns() {
                return scheduleRunRepository.findAllByOrderByCreatedAtDesc().stream()
                                .map(ScheduleRunDTO::new)
                                .collect(Collectors.toList());
        }

        @GetMapping("/view")
        public ScheduleViewDTO getScheduleView(@RequestParam(required = false) Integer runId) {
                List<ResolvedAssignment> assignments = resolveAssignments(runId).stream()
                                .filter(a -> a.blockTimeslotId() != null)
                                .collect(Collectors.toList());

                ScheduleViewDTO view = buildScheduleView(assignments);
                view.setUnassignedCount(assignmentRepository.findUnassignedBlocks().size());
                return view;
        }

        @GetMapping("/view/group/{groupId}")
        public ScheduleViewDTO getScheduleViewByGroup(@PathVariable String groupId,
                        @RequestParam(required = false) Integer runId) {
                List<ResolvedAssignment> assignments = resolveAssignments(runId).stream()
                                .filter(a -> groupId.equals(a.groupId()) && a.blockTimeslotId() != null)
                                .collect(Collectors.toList());

                return buildScheduleView(assignments);
        }

        @GetMapping("/view/teacher/{teacherId}")
        public ScheduleViewDTO getScheduleViewByTeacher(@PathVariable String teacherId,
                        @RequestParam(required = false) Integer runId) {
                return buildScheduleView(assignmentsForTeacher(teacherId, runId));
        }

        /**
         * A TEACHER-role account's own schedule, resolved from their app_user.teacher_id
         * link rather than a path parameter - unlike the other /view/* endpoints, this
         * one is reachable by the TEACHER role (see SecurityConfig), which has no
         * broader read access to pick another teacher's schedule.
         */
        @GetMapping("/view/me")
        public ScheduleViewDTO getMyScheduleView(Authentication authentication,
                        @RequestParam(required = false) Integer runId) {
                String teacherId = appUserRepository.findById(authentication.getName())
                                .map(AppUserEntity::getTeacherId)
                                .orElse(null);
                if (teacherId == null) {
                        return buildScheduleView(Collections.emptyList());
                }
                return buildScheduleView(assignmentsForTeacher(teacherId, runId));
        }

        @GetMapping("/view/room/{roomName}")
        public ScheduleViewDTO getScheduleViewByRoom(@PathVariable String roomName,
                        @RequestParam(required = false) Integer runId) {
                List<ResolvedAssignment> assignments = resolveAssignments(runId).stream()
                                .filter(a -> roomName.equals(a.roomName()) && a.blockTimeslotId() != null)
                                .collect(Collectors.toList());

                return buildScheduleView(assignments);
        }

        private List<ResolvedAssignment> assignmentsForTeacher(String teacherId, Integer runId) {
                return resolveAssignments(runId).stream()
                                .filter(a -> teacherId.equals(a.teacherId()) && a.blockTimeslotId() != null)
                                .collect(Collectors.toList());
        }

        /**
         * Resolves every assignment's effective timeslot for the requested run: null
         * runId means "the current schedule" (course_block_assignment_current - pinned
         * rows keep their own input timeslot, everything else is the latest run),
         * matching every /view* endpoint's default behavior before run selection
         * existed. A specific runId instead reads schedule_run_result's own frozen
         * snapshot directly - it carries both that run's solved timeslot AND a copy
         * of every input field (group/course/teacher/room/pinned/etc.) as they were
         * at solve time, so a later edit to course_block_assignment (or a rename/
         * deletion of the referenced teacher/room/course/group) can't retroactively
         * change what a historical run is shown to have used. Course/teacher/room/
         * group *display names* are still looked up from today's reference data in
         * buildScheduleView below - only the ids/assignment fields are frozen.
         */
        private List<ResolvedAssignment> resolveAssignments(Integer runId) {
                if (runId == null) {
                        return assignmentCurrentRepository.findAll().stream()
                                        .map(ResolvedAssignment::fromCurrent)
                                        .collect(Collectors.toList());
                }
                return scheduleRunResultRepository.findByScheduleRunId(runId).stream()
                                .map(ResolvedAssignment::fromRunResult)
                                .collect(Collectors.toList());
        }

        private ScheduleViewDTO buildScheduleView(List<ResolvedAssignment> assignments) {
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

                for (ResolvedAssignment assignment : assignments) {
                        BlockTimeslotEntity timeslot = timeslots.get(assignment.blockTimeslotId());
                        CourseEntity course = courses.get(assignment.courseId());
                        TeacherEntity teacher = teachers.get(assignment.teacherId());
                        RoomEntity room = rooms.get(assignment.roomName());
                        StudentGroupEntity group = groups.get(assignment.groupId());

                        if (timeslot != null && course != null) {
                                ScheduleViewDTO.ScheduleEntry entry = new ScheduleViewDTO.ScheduleEntry();
                                entry.setId(assignment.id());
                                entry.setDayOfWeek(timeslot.getDayOfWeek());
                                entry.setStartHour(timeslot.getStartHour());
                                entry.setLengthHours(timeslot.getLengthHours());
                                entry.setCourseName(course.getName());
                                entry.setTeacherName(teacher != null ? teacher.getName() + " " + teacher.getLastName()
                                                : null);
                                entry.setTeacherId(teacher != null ? teacher.getId() : null);
                                entry.setRoomName(room != null ? room.getName() : null);
                                entry.setGroupName(group != null ? group.getName() : null);
                                entry.setGroupId(group != null ? group.getId() : null);
                                entry.setPinned(assignment.pinned());

                                entries.add(entry);
                        }
                }

                ScheduleViewDTO view = new ScheduleViewDTO();
                view.setEntries(entries);
                view.setTotalAssignments(assignments.size());
                view.setAssignedCount(assignments.size());

                return view;
        }

        /**
         * A resolved assignment's display-relevant fields, decoupled from which
         * repository/entity produced them (the always-latest view for the default
         * case, or a specific run's own frozen snapshot otherwise).
         */
        private record ResolvedAssignment(String id, String groupId, String courseId, String teacherId,
                        String roomName, String blockTimeslotId, Boolean pinned) {

                static ResolvedAssignment fromCurrent(CourseBlockAssignmentCurrentEntity a) {
                        return new ResolvedAssignment(a.getId(), a.getGroupId(), a.getCourseId(), a.getTeacherId(),
                                        a.getRoomName(), a.getBlockTimeslotId(), a.getPinned());
                }

                static ResolvedAssignment fromRunResult(ScheduleRunResultEntity r) {
                        return new ResolvedAssignment(r.getAssignmentId(), r.getGroupId(), r.getCourseId(),
                                        r.getTeacherId(), r.getRoomName(), r.getBlockTimeslotId(), r.getPinned());
                }
        }
}
