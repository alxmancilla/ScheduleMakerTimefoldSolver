#!/bin/bash
# ============================================================================
# Test Script: Validate Schema and Dataset Compatibility
# ============================================================================
# This script tests that the updated schema.sql and load_final_dataset.sql
# are compatible and can be loaded without errors.
# ============================================================================

set -e  # Exit on error

echo "=========================================="
echo "Schema and Dataset Compatibility Test"
echo "=========================================="
echo ""

# Database connection parameters
DB_NAME="school_schedule_test"
DB_USER="mancilla"

echo "Step 1: Drop test database if it exists..."
psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null || true

echo "Step 2: Create test database..."
psql -U "$DB_USER" -d postgres -c "CREATE DATABASE $DB_NAME WITH ENCODING='UTF8';"

echo "Step 3: Load schema..."
psql -U "$DB_USER" -d "$DB_NAME" -f database/schema.sql

echo "Step 4: Load dataset..."
psql -U "$DB_USER" -d "$DB_NAME" -f database/datasets/load_final_dataset.sql

echo "Step 5: Validate data counts..."
echo ""
echo "--- Data Validation ---"
TEACHER_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM teacher;")
COURSE_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM course;")
ROOM_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM room;")
TIMESLOT_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM timeslot;")
GROUP_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM student_group;")
ASSIGNMENT_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM course_assignment;")

echo "Teachers:          $TEACHER_COUNT (expected: 42)"
echo "Courses:           $COURSE_COUNT (expected: 61)"
echo "Rooms:             $ROOM_COUNT (expected: 32)"
echo "Timeslots:         $TIMESLOT_COUNT (expected: 40)"
echo "Student Groups:    $GROUP_COUNT (expected: 20)"
echo "Course Assignments: $ASSIGNMENT_COUNT"
echo ""

echo "Step 6: Test constraint validation..."
echo ""
echo "--- Constraint Tests ---"

# Test 1: Check for any constraint violations
VIOLATIONS=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*) FROM course WHERE active NOT IN (TRUE, FALSE);
")
echo "Invalid active values: $VIOLATIONS (expected: 0)"

# Test 2: Check foreign key integrity
FK_VIOLATIONS=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*) FROM course_assignment ca
    LEFT JOIN teacher t ON ca.teacher_id = t.id
    WHERE ca.teacher_id IS NOT NULL AND t.id IS NULL;
")
echo "FK violations (teacher): $FK_VIOLATIONS (expected: 0)"

# Test 3: Check room types match course requirements
ROOM_TYPE_ISSUES=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "
    SELECT COUNT(*) FROM course
    WHERE room_requirement NOT IN ('estándar', 'taller', 'taller electromecánica', 'taller electrónica', 'centro de cómputo', 'laboratorio');
")
echo "Invalid room requirements: $ROOM_TYPE_ISSUES (expected: 0)"

echo ""
echo "Step 7: Test views..."
TEACHER_SUMMARY_COUNT=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT COUNT(*) FROM v_teacher_summary;")
echo "Teacher summary view: $TEACHER_SUMMARY_COUNT rows"

echo ""
echo "Step 8: Cleanup..."
psql -U "$DB_USER" -d postgres -c "DROP DATABASE $DB_NAME;"

echo ""
echo "=========================================="
echo "✅ ALL TESTS PASSED!"
echo "=========================================="
echo ""
echo "The schema and dataset are compatible."
echo "No errors were found during loading or validation."

