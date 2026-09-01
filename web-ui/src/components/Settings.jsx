import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getTimeslots, createTimeslot, updateTimeslot, deleteTimeslot,
  runEngine, getEngineStatus, generateBlocks, clearUnpinnedTimeslots,
  listAdminReports, downloadAdminReport,
  getTerm, updateTerm, getAuditLog,
  getComponentBlockRules, setComponentBlockRule, deleteComponentBlockRule, getCourseDesignations,
  getConstraintWeights, setConstraintWeight, deleteConstraintWeight,
  getSemesterHourLimits, setSemesterHourLimit, deleteSemesterHourLimit,
  getCalendarExceptions, setCalendarException, deleteCalendarException,
  exportDatabase, importDatabase, getDatabaseBackupStatus, listDatabaseBackups, downloadDatabaseBackup,
} from '../api';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';
import { formatHour } from '../constants';

const ENGINE_POLL_MS = 3000;

const DAY_LABELS = [
  { value: 1, key: 'mon' },
  { value: 2, key: 'tue' },
  { value: 3, key: 'wed' },
  { value: 4, key: 'thu' },
  { value: 5, key: 'fri' },
];
const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');
const START_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];
const LENGTHS = [1, 2, 3, 4];
const EMPTY_FORM = { dayOfWeek: 1, startHour: 7, lengthHours: 1 };
const BLOCK_SIZES = [1, 2, 3, 4];
const EMPTY_BLOCK_RULE_FORM = { component: '', preferredBlockSize: 2, maxBlocksPerDay: 2 };
const SEVERITY_OPTIONS = ['HARD', 'SOFT'];
const EMPTY_SEMESTER_HOUR_LIMIT_FORM = { semester: '', latestEndHour: 14, severity: 'HARD' };
const CALENDAR_EXCEPTION_TYPES = ['HOLIDAY', 'HALF_DAY', 'EXAM_DAY'];
const EMPTY_CALENDAR_EXCEPTION_FORM = { date: '', type: 'HOLIDAY', label: '', endHour: 12 };

const SETTINGS_TABS = [
  { key: 'term', labelKey: 'settings.term.title' },
  { key: 'solver', labelKey: 'settings.solver.title' },
  { key: 'complianceSnapshots', labelKey: 'settings.complianceSnapshots.title' },
  { key: 'generateBlocks', labelKey: 'settings.generateBlocks.title' },
  { key: 'blockRules', labelKey: 'settings.blockRules.title' },
  { key: 'constraintWeights', labelKey: 'settings.constraintWeights.title' },
  { key: 'semesterHourLimits', labelKey: 'settings.semesterHourLimits.title' },
  { key: 'calendar', labelKey: 'settings.calendar.title' },
  { key: 'timeslots', labelKey: 'settings.timeslots.title' },
  { key: 'databaseBackups', labelKey: 'settings.databaseBackups.title' },
  { key: 'auditLog', labelKey: 'settings.auditLog.title' },
];

const formatBytes = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

function Settings() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const dayLabel = (value) => t(`common.days.${DAY_LABELS.find((d) => d.value === value)?.key || 'mon'}`);

  const [timeslots, setTimeslots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingTimeslot, setEditingTimeslot] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const [engineStatus, setEngineStatus] = useState(null);
  const [engineError, setEngineError] = useState(null);
  // Pre-filled with solverConfig.xml's own defaults; omitted from the request
  // (left as null) only if the field is cleared entirely, which leaves that
  // value as the XML defines it.
  const [minutesSpentLimit, setMinutesSpentLimit] = useState(5);
  const [unimprovedMinutesSpentLimit, setUnimprovedMinutesSpentLimit] = useState(2);
  const pollRef = useRef(null);

  const [adminReports, setAdminReports] = useState([]);
  const [adminReportsError, setAdminReportsError] = useState(null);
  const [openingSnapshot, setOpeningSnapshot] = useState(null);

  const [termInput, setTermInput] = useState('');
  const [termError, setTermError] = useState(null);
  const [savingTerm, setSavingTerm] = useState(false);

  const [auditLog, setAuditLog] = useState([]);
  const [auditLogError, setAuditLogError] = useState(null);

  const [blockRules, setBlockRules] = useState([]);
  const [blockRulesError, setBlockRulesError] = useState(null);
  const [courseDesignations, setCourseDesignations] = useState([]);
  const [showBlockRuleForm, setShowBlockRuleForm] = useState(false);
  const [editingBlockRuleComponent, setEditingBlockRuleComponent] = useState(null);
  const [blockRuleForm, setBlockRuleForm] = useState(EMPTY_BLOCK_RULE_FORM);
  const [savingBlockRule, setSavingBlockRule] = useState(false);

  const [constraintWeights, setConstraintWeights] = useState([]);
  const [constraintWeightsError, setConstraintWeightsError] = useState(null);
  const [weightDrafts, setWeightDrafts] = useState({});
  const [savingWeightFor, setSavingWeightFor] = useState(null);

  const [semesterHourLimits, setSemesterHourLimits] = useState([]);
  const [semesterHourLimitsError, setSemesterHourLimitsError] = useState(null);
  const [showSemesterHourLimitForm, setShowSemesterHourLimitForm] = useState(false);
  const [editingSemester, setEditingSemester] = useState(null);
  const [semesterHourLimitForm, setSemesterHourLimitForm] = useState(EMPTY_SEMESTER_HOUR_LIMIT_FORM);
  const [savingSemesterHourLimit, setSavingSemesterHourLimit] = useState(false);

  const [calendarExceptions, setCalendarExceptions] = useState([]);
  const [calendarExceptionsError, setCalendarExceptionsError] = useState(null);
  const [showCalendarExceptionForm, setShowCalendarExceptionForm] = useState(false);
  const [editingCalendarExceptionDate, setEditingCalendarExceptionDate] = useState(null);
  const [calendarExceptionForm, setCalendarExceptionForm] = useState(EMPTY_CALENDAR_EXCEPTION_FORM);
  const [savingCalendarException, setSavingCalendarException] = useState(false);

  const [dbBackups, setDbBackups] = useState([]);
  const [dbBackupsError, setDbBackupsError] = useState(null);
  const [dbStatus, setDbStatus] = useState(null);
  const [dbError, setDbError] = useState(null);
  const [downloadingBackup, setDownloadingBackup] = useState(null);
  const dbPollRef = useRef(null);

  const [activeTab, setActiveTab] = useState('term');

  useEffect(() => {
    loadTimeslots();
    loadEngineStatus();
    loadBlockRules();
    loadCourseDesignations();
    loadConstraintWeights();
    loadSemesterHourLimits();
    loadCalendarExceptions();
    loadAdminReports();
    loadTerm();
    loadAuditLog();
    loadDatabaseBackups();
    loadDatabaseStatus();
    return () => {
      stopPolling();
      stopDbPolling();
    };
  }, []);

  const loadAuditLog = async () => {
    try {
      const response = await getAuditLog();
      setAuditLog(response.data);
      setAuditLogError(null);
    } catch (err) {
      setAuditLogError(t('settings.auditLog.loadFailedPrefix') + err.message);
    }
  };

  const loadTerm = async () => {
    try {
      const response = await getTerm();
      setTermInput(response.data.label || '');
      setTermError(null);
    } catch (err) {
      setTermError(t('settings.term.loadFailedPrefix') + err.message);
    }
  };

  const handleSaveTerm = async (e) => {
    e.preventDefault();
    setSavingTerm(true);
    setTermError(null);
    try {
      const response = await updateTerm(termInput.trim());
      setTermInput(response.data.label || '');
      showToast(t('settings.term.savedMessage'));
    } catch (err) {
      setTermError(err.response?.data?.message || t('settings.term.saveFailedPrefix') + err.message);
    } finally {
      setSavingTerm(false);
    }
  };

  const loadAdminReports = async () => {
    try {
      const response = await listAdminReports();
      setAdminReports(response.data);
      setAdminReportsError(null);
    } catch (err) {
      setAdminReportsError(t('settings.complianceSnapshots.loadFailedPrefix') + err.message);
    }
  };

  const handleViewSnapshot = async (runId, filename) => {
    const key = `${runId}::${filename}`;
    setOpeningSnapshot(key);
    try {
      const response = await downloadAdminReport(runId, filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setAdminReportsError(t('settings.complianceSnapshots.openFailedPrefix') + filename + ': ' + err.message);
    } finally {
      setOpeningSnapshot(null);
    }
  };

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(loadEngineStatus, ENGINE_POLL_MS);
  };

  const loadEngineStatus = async () => {
    try {
      const response = await getEngineStatus();
      setEngineStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!pollRef.current) startPolling();
      } else {
        stopPolling();
        // A run that just finished may have added a new compliance snapshot.
        loadAdminReports();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleRunEngine = async () => {
    setEngineError(null);
    try {
      const response = await runEngine({
        minutesSpentLimit: minutesSpentLimit === '' ? null : minutesSpentLimit,
        unimprovedMinutesSpentLimit: unimprovedMinutesSpentLimit === '' ? null : unimprovedMinutesSpentLimit,
      });
      setEngineStatus(response.data);
      startPolling();
    } catch (err) {
      setEngineError(err.response?.data?.message || t('settings.solver.startFailedPrefix') + err.message);
    }
  };

  const loadDatabaseBackups = async () => {
    try {
      const response = await listDatabaseBackups();
      setDbBackups(response.data);
      setDbBackupsError(null);
    } catch (err) {
      setDbBackupsError(t('settings.databaseBackups.loadFailedPrefix') + err.message);
    }
  };

  const stopDbPolling = () => {
    if (dbPollRef.current) {
      clearInterval(dbPollRef.current);
      dbPollRef.current = null;
    }
  };

  const startDbPolling = () => {
    stopDbPolling();
    dbPollRef.current = setInterval(loadDatabaseStatus, ENGINE_POLL_MS);
  };

  const loadDatabaseStatus = async () => {
    try {
      const response = await getDatabaseBackupStatus();
      setDbStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!dbPollRef.current) startDbPolling();
      } else {
        stopDbPolling();
        // A run that just finished may have added/replaced a backup file.
        loadDatabaseBackups();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleExportDatabase = async () => {
    setDbError(null);
    try {
      const response = await exportDatabase();
      setDbStatus(response.data);
      startDbPolling();
    } catch (err) {
      setDbError(err.response?.data?.message || t('settings.databaseBackups.exportFailedPrefix') + err.message);
    }
  };

  const handleImportDatabase = async (filename) => {
    if (!(await confirmAction(t('settings.databaseBackups.importConfirm', { filename })))) return;
    setDbError(null);
    try {
      const response = await importDatabase(filename);
      setDbStatus(response.data);
      startDbPolling();
    } catch (err) {
      setDbError(err.response?.data?.message || t('settings.databaseBackups.importFailedPrefix') + err.message);
    }
  };

  const handleDownloadBackup = async (filename) => {
    setDownloadingBackup(filename);
    try {
      const response = await downloadDatabaseBackup(filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setDbBackupsError(t('settings.databaseBackups.downloadFailedPrefix') + filename + ': ' + err.message);
    } finally {
      setDownloadingBackup(null);
    }
  };

  const [blockGenResult, setBlockGenResult] = useState(null);
  const [blockGenError, setBlockGenError] = useState(null);
  const [generatingBlocks, setGeneratingBlocks] = useState(false);

  const handleGenerateBlocks = async () => {
    setGeneratingBlocks(true);
    setBlockGenError(null);
    setBlockGenResult(null);
    try {
      const response = await generateBlocks();
      setBlockGenResult(response.data);
    } catch (err) {
      setBlockGenError(err.response?.data?.message || t('settings.generateBlocks.failedPrefix') + err.message);
    } finally {
      setGeneratingBlocks(false);
    }
  };

  const [clearTimeslotsResult, setClearTimeslotsResult] = useState(null);
  const [clearTimeslotsError, setClearTimeslotsError] = useState(null);
  const [clearingTimeslots, setClearingTimeslots] = useState(false);

  const handleClearUnpinnedTimeslots = async () => {
    if (!(await confirmAction(t('settings.generateBlocks.clearTimeslots.confirm')))) return;
    setClearingTimeslots(true);
    setClearTimeslotsError(null);
    setClearTimeslotsResult(null);
    try {
      const response = await clearUnpinnedTimeslots();
      setClearTimeslotsResult(response.data);
    } catch (err) {
      setClearTimeslotsError(
        err.response?.data?.message || t('settings.generateBlocks.clearTimeslots.failedPrefix') + err.message
      );
    } finally {
      setClearingTimeslots(false);
    }
  };

  const loadBlockRules = async () => {
    try {
      const response = await getComponentBlockRules();
      setBlockRules(response.data);
      setBlockRulesError(null);
    } catch (err) {
      setBlockRulesError(t('settings.blockRules.loadFailedPrefix') + err.message);
    }
  };

  const loadCourseDesignations = async () => {
    try {
      const response = await getCourseDesignations();
      setCourseDesignations(response.data);
    } catch (err) {
      // Non-critical: the "add rule" component dropdown just won't have options.
    }
  };

  const unconfiguredComponents = courseDesignations.filter(
    (c) => !blockRules.some((r) => r.component === c)
  );

  const handleAddBlockRule = () => {
    setEditingBlockRuleComponent(null);
    setBlockRuleForm({ component: unconfiguredComponents[0] || '', preferredBlockSize: 2, maxBlocksPerDay: 2 });
    setBlockRulesError(null);
    setShowBlockRuleForm(true);
  };

  const handleEditBlockRule = (rule) => {
    setEditingBlockRuleComponent(rule.component);
    setBlockRuleForm({ component: rule.component, preferredBlockSize: rule.preferredBlockSize, maxBlocksPerDay: rule.maxBlocksPerDay });
    setBlockRulesError(null);
    setShowBlockRuleForm(true);
  };

  const handleCancelBlockRule = () => {
    setShowBlockRuleForm(false);
    setEditingBlockRuleComponent(null);
    setBlockRulesError(null);
  };

  const handleSubmitBlockRule = async (e) => {
    e.preventDefault();
    setSavingBlockRule(true);
    setBlockRulesError(null);
    try {
      await setComponentBlockRule(blockRuleForm.component, blockRuleForm.preferredBlockSize, blockRuleForm.maxBlocksPerDay);
      handleCancelBlockRule();
      loadBlockRules();
      showToast(t('settings.blockRules.savedMessage'));
    } catch (err) {
      setBlockRulesError(err.response?.data?.message || t('settings.blockRules.saveFailedPrefix') + err.message);
    } finally {
      setSavingBlockRule(false);
    }
  };

  const handleDeleteBlockRule = async (component) => {
    if (!(await confirmAction(t('settings.blockRules.confirmDelete', { component })))) return;
    try {
      await deleteComponentBlockRule(component);
      loadBlockRules();
      showToast(t('settings.blockRules.deletedMessage'));
    } catch (err) {
      setBlockRulesError(err.response?.data?.message || t('settings.blockRules.deleteFailedPrefix') + err.message);
    }
  };

  // Soft-constraint weight overrides: every known constraint (from
  // scheduler-common's SoftConstraintDefaults, via GET /api/admin/constraint-config)
  // with its default, current override (if any), and effective weight.
  // weightDrafts holds each row's editable value locally until Saved.
  const loadConstraintWeights = async () => {
    try {
      const response = await getConstraintWeights();
      setConstraintWeights(response.data);
      const drafts = {};
      response.data.forEach((row) => {
        drafts[row.constraintName] = row.effectiveWeight;
      });
      setWeightDrafts(drafts);
      setConstraintWeightsError(null);
    } catch (err) {
      setConstraintWeightsError(t('settings.constraintWeights.loadFailedPrefix') + err.message);
    }
  };

  const handleWeightDraftChange = (constraintName, value) => {
    setWeightDrafts({ ...weightDrafts, [constraintName]: value });
  };

  const handleSaveWeight = async (constraintName) => {
    const value = parseInt(weightDrafts[constraintName], 10);
    if (Number.isNaN(value)) return;
    setSavingWeightFor(constraintName);
    try {
      await setConstraintWeight(constraintName, value);
      await loadConstraintWeights();
      showToast(t('settings.constraintWeights.savedMessage'));
    } catch (err) {
      setConstraintWeightsError(err.response?.data?.message || t('settings.constraintWeights.saveFailedPrefix') + err.message);
    } finally {
      setSavingWeightFor(null);
    }
  };

  const handleResetWeight = async (constraintName) => {
    setSavingWeightFor(constraintName);
    try {
      await deleteConstraintWeight(constraintName);
      await loadConstraintWeights();
      showToast(t('settings.constraintWeights.resetMessage'));
    } catch (err) {
      setConstraintWeightsError(err.response?.data?.message || t('settings.constraintWeights.saveFailedPrefix') + err.message);
    } finally {
      setSavingWeightFor(null);
    }
  };

  // Per-semester finish-by-hour limits: an open-ended set of semesters (not
  // a fixed list like constraint weights), so this follows the Block Rules
  // add/edit-modal pattern instead of the constraint-weights fixed-table one.
  const loadSemesterHourLimits = async () => {
    try {
      const response = await getSemesterHourLimits();
      setSemesterHourLimits(response.data);
      setSemesterHourLimitsError(null);
    } catch (err) {
      setSemesterHourLimitsError(t('settings.semesterHourLimits.loadFailedPrefix') + err.message);
    }
  };

  const handleAddSemesterHourLimit = () => {
    setEditingSemester(null);
    setSemesterHourLimitForm(EMPTY_SEMESTER_HOUR_LIMIT_FORM);
    setSemesterHourLimitsError(null);
    setShowSemesterHourLimitForm(true);
  };

  const handleEditSemesterHourLimit = (limit) => {
    setEditingSemester(limit.semester);
    setSemesterHourLimitForm({
      semester: limit.semester,
      latestEndHour: limit.latestEndHour,
      severity: limit.severity,
    });
    setSemesterHourLimitsError(null);
    setShowSemesterHourLimitForm(true);
  };

  const handleCancelSemesterHourLimit = () => {
    setShowSemesterHourLimitForm(false);
    setEditingSemester(null);
    setSemesterHourLimitsError(null);
  };

  const handleSubmitSemesterHourLimit = async (e) => {
    e.preventDefault();
    setSavingSemesterHourLimit(true);
    setSemesterHourLimitsError(null);
    try {
      await setSemesterHourLimit(
        semesterHourLimitForm.semester,
        semesterHourLimitForm.latestEndHour,
        semesterHourLimitForm.severity
      );
      handleCancelSemesterHourLimit();
      loadSemesterHourLimits();
      showToast(t('settings.semesterHourLimits.savedMessage'));
    } catch (err) {
      setSemesterHourLimitsError(err.response?.data?.message || t('settings.semesterHourLimits.saveFailedPrefix') + err.message);
    } finally {
      setSavingSemesterHourLimit(false);
    }
  };

  const handleDeleteSemesterHourLimit = async (semester) => {
    if (!(await confirmAction(t('settings.semesterHourLimits.confirmDelete', { semester })))) return;
    try {
      await deleteSemesterHourLimit(semester);
      loadSemesterHourLimits();
      showToast(t('settings.semesterHourLimits.deletedMessage'));
    } catch (err) {
      setSemesterHourLimitsError(err.response?.data?.message || t('settings.semesterHourLimits.deleteFailedPrefix') + err.message);
    }
  };

  const loadCalendarExceptions = async () => {
    try {
      const response = await getCalendarExceptions();
      setCalendarExceptions(response.data);
      setCalendarExceptionsError(null);
    } catch (err) {
      setCalendarExceptionsError(t('settings.calendar.loadFailedPrefix') + err.message);
    }
  };

  const handleAddCalendarException = () => {
    setEditingCalendarExceptionDate(null);
    setCalendarExceptionForm(EMPTY_CALENDAR_EXCEPTION_FORM);
    setCalendarExceptionsError(null);
    setShowCalendarExceptionForm(true);
  };

  const handleEditCalendarException = (exception) => {
    setEditingCalendarExceptionDate(exception.exceptionDate);
    setCalendarExceptionForm({
      date: exception.exceptionDate,
      type: exception.type,
      label: exception.label || '',
      endHour: exception.endHour || 12,
    });
    setCalendarExceptionsError(null);
    setShowCalendarExceptionForm(true);
  };

  const handleCancelCalendarException = () => {
    setShowCalendarExceptionForm(false);
    setEditingCalendarExceptionDate(null);
    setCalendarExceptionsError(null);
  };

  const handleSubmitCalendarException = async (e) => {
    e.preventDefault();
    setSavingCalendarException(true);
    setCalendarExceptionsError(null);
    try {
      const endHour = calendarExceptionForm.type === 'HALF_DAY' ? calendarExceptionForm.endHour : null;
      await setCalendarException(calendarExceptionForm.date, calendarExceptionForm.type,
          calendarExceptionForm.label || null, endHour);
      handleCancelCalendarException();
      loadCalendarExceptions();
      showToast(t('settings.calendar.savedMessage'));
    } catch (err) {
      setCalendarExceptionsError(err.response?.data?.message || t('settings.calendar.saveFailedPrefix') + err.message);
    } finally {
      setSavingCalendarException(false);
    }
  };

  const handleDeleteCalendarException = async (date) => {
    if (!(await confirmAction(t('settings.calendar.confirmDelete', { date })))) return;
    try {
      await deleteCalendarException(date);
      loadCalendarExceptions();
      showToast(t('settings.calendar.deletedMessage'));
    } catch (err) {
      setCalendarExceptionsError(err.response?.data?.message || t('settings.calendar.deleteFailedPrefix') + err.message);
    }
  };

  const loadTimeslots = async () => {
    try {
      setLoading(true);
      const response = await getTimeslots();
      setTimeslots(response.data);
      setError(null);
    } catch (err) {
      setError(t('settings.timeslots.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleField = (e) => {
    setForm({ ...form, [e.target.name]: parseInt(e.target.value, 10) });
  };

  const handleAdd = () => {
    setEditingTimeslot(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleEdit = (timeslot) => {
    setEditingTimeslot(timeslot);
    setForm({
      dayOfWeek: timeslot.dayOfWeek,
      startHour: timeslot.startHour,
      lengthHours: timeslot.lengthHours,
    });
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingTimeslot(null);
    setFieldErrors({});
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);

    const payload = {
      dayOfWeek: form.dayOfWeek,
      startHour: form.startHour,
      lengthHours: form.lengthHours,
    };

    try {
      if (editingTimeslot) {
        await updateTimeslot(editingTimeslot.id, payload);
      } else {
        await createTimeslot(payload);
      }
      handleCancel();
      loadTimeslots();
      showToast(t('settings.timeslots.savedMessage'));
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('settings.timeslots.saveFailedPrefix') + err.message);
    }
  };

  const handleDelete = async (timeslot) => {
    if (!(await confirmAction(t('settings.timeslots.confirmDelete', {
      day: dayLabel(timeslot.dayOfWeek),
      start: timeslot.startHour,
      end: timeslot.startHour + timeslot.lengthHours,
    })))) return;
    try {
      await deleteTimeslot(timeslot.id);
      loadTimeslots();
      showToast(t('settings.timeslots.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('settings.timeslots.deleteFailedPrefix') + err.message);
    }
  };

  const endHour = form.startHour + form.lengthHours;
  const exceedsDayBounds = endHour > 15;

  const auditLogPagination = usePagination(auditLog);

  if (loading) return <div className="loading">{t('settings.loading')}</div>;

  return (
    <div>
      <div className="card">
        <h2>{t('settings.title')}</h2>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '14px' }}>{t('settings.description')}</p>
      </div>

      <div className="tabs" role="tablist">
        {SETTINGS_TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            id={`settings-tab-${tab.key}`}
            role="tab"
            aria-selected={activeTab === tab.key}
            aria-controls={`settings-panel-${tab.key}`}
            className={`tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {activeTab === 'term' && (
      <div role="tabpanel" id="settings-panel-term" aria-labelledby="settings-tab-term">
      <div className="card">
        <h3>{t('settings.term.title')}</h3>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.term.description')}
        </p>
        {termError && <div className="error" role="alert">{termError}</div>}
        <form onSubmit={handleSaveTerm} style={{ display: 'flex', gap: '10px', marginTop: '10px', alignItems: 'flex-end' }}>
          <div className="form-group" style={{ marginBottom: 0, flex: 1, maxWidth: '320px' }}>
            <label>{t('settings.term.fields.label')}</label>
            <input
              type="text"
              value={termInput}
              onChange={(e) => setTermInput(e.target.value)}
              maxLength={100}
              placeholder={t('settings.term.placeholder')}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={savingTerm}>
            {savingTerm ? t('settings.term.saving') : t('common.save')}
          </button>
        </form>
      </div>
      </div>
      )}

      {activeTab === 'solver' && (
      <div role="tabpanel" id="settings-panel-solver" aria-labelledby="settings-tab-solver">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <h3>{t('settings.solver.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleRunEngine}
            disabled={engineStatus?.state === 'RUNNING'}
          >
            {engineStatus?.state === 'RUNNING' ? t('settings.solver.running') : `▶ ${t('settings.solver.startEngine')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.solver.description')}
        </p>
        <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap', marginTop: '12px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="minutesSpentLimit">{t('settings.solver.minutesSpentLimit')}</label>
            <input
              id="minutesSpentLimit"
              type="number"
              min="1"
              max="120"
              value={minutesSpentLimit}
              onChange={(e) => setMinutesSpentLimit(e.target.value === '' ? '' : Number(e.target.value))}
              disabled={engineStatus?.state === 'RUNNING'}
              style={{ width: '70px', padding: '6px' }}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="unimprovedMinutesSpentLimit">{t('settings.solver.unimprovedMinutesSpentLimit')}</label>
            <input
              id="unimprovedMinutesSpentLimit"
              type="number"
              min="1"
              max="60"
              value={unimprovedMinutesSpentLimit}
              onChange={(e) => setUnimprovedMinutesSpentLimit(e.target.value === '' ? '' : Number(e.target.value))}
              disabled={engineStatus?.state === 'RUNNING'}
              style={{ width: '70px', padding: '6px' }}
            />
          </div>
        </div>
        <p style={{ marginTop: '6px', color: 'var(--color-text-secondary)', fontSize: '12px' }}>
          {t('settings.solver.limitsDescription')}
        </p>
        {engineError && <div className="error" role="alert">{engineError}</div>}
        {engineStatus && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('settings.solver.state')}</strong> {engineStatus.state}</span>
              <span><strong>{t('settings.solver.started')}</strong> {formatTimestamp(engineStatus.startedAt)}</span>
              <span><strong>{t('settings.solver.finished')}</strong> {formatTimestamp(engineStatus.finishedAt)}</span>
              <span><strong>{t('settings.solver.exitCode')}</strong> {engineStatus.exitCode ?? '-'}</span>
            </div>
            {engineStatus.log && engineStatus.log.length > 0 && (
              <pre
                style={{
                  marginTop: '10px',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: '10px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {engineStatus.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>
      </div>
      )}

      {activeTab === 'complianceSnapshots' && (
      <div role="tabpanel" id="settings-panel-complianceSnapshots" aria-labelledby="settings-tab-complianceSnapshots">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.complianceSnapshots.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAdminReports}>↻ {t('settings.complianceSnapshots.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.complianceSnapshots.description')}
        </p>
        {adminReportsError && <div className="error" role="alert">{adminReportsError}</div>}
        {adminReports.length === 0 && !adminReportsError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.complianceSnapshots.none')}</p>
        )}
        {adminReports.length > 0 && (
          <table style={{ marginTop: '8px' }}>
            <thead>
              <tr>
                <th>{t('settings.complianceSnapshots.table.run')}</th>
                <th>{t('settings.complianceSnapshots.table.file')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {adminReports.map((run) =>
                run.files.map((f) => {
                  const key = `${run.runId}::${f.filename}`;
                  return (
                    <tr key={key}>
                      <td>{formatTimestamp(run.generatedAt)}</td>
                      <td>{f.filename}</td>
                      <td>
                        <button
                          className="btn btn-primary"
                          onClick={() => handleViewSnapshot(run.runId, f.filename)}
                          disabled={openingSnapshot === key}
                        >
                          {openingSnapshot === key ? t('settings.complianceSnapshots.opening') : t('settings.complianceSnapshots.view')}
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'generateBlocks' && (
      <div role="tabpanel" id="settings-panel-generateBlocks" aria-labelledby="settings-tab-generateBlocks">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.generateBlocks.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleGenerateBlocks}
            disabled={generatingBlocks}
          >
            {generatingBlocks ? t('settings.generateBlocks.generating') : `⚙ ${t('settings.generateBlocks.button')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.generateBlocks.description')}
        </p>
        {blockGenError && <div className="error" role="alert">{blockGenError}</div>}
        {blockGenResult && (
          <div style={{ marginTop: '10px', fontSize: '13px' }}>
            <div style={{ color: '#2e7d32' }}>
              {t('settings.generateBlocks.resultSummary', {
                created: blockGenResult.blocksCreated,
                skipped: blockGenResult.groupCoursesSkippedExisting,
              })}
            </div>
            {blockGenResult.warnings.length > 0 && (
              <ul style={{ marginTop: '6px', color: 'var(--color-danger-dark)' }}>
                {blockGenResult.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.generateBlocks.clearTimeslots.title')}</h3>
          <button
            className="btn btn-danger"
            onClick={handleClearUnpinnedTimeslots}
            disabled={clearingTimeslots}
          >
            {clearingTimeslots
              ? t('settings.generateBlocks.clearTimeslots.clearing')
              : t('settings.generateBlocks.clearTimeslots.button')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.generateBlocks.clearTimeslots.description')}
        </p>
        {clearTimeslotsError && <div className="error" role="alert">{clearTimeslotsError}</div>}
        {clearTimeslotsResult && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: '#2e7d32' }}>
            {t('settings.generateBlocks.clearTimeslots.resultSummary', {
              count: clearTimeslotsResult.clearedCount,
            })}
          </div>
        )}
      </div>
      </div>
      )}

      {activeTab === 'blockRules' && (
      <div role="tabpanel" id="settings-panel-blockRules" aria-labelledby="settings-tab-blockRules">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.blockRules.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleAddBlockRule}
            disabled={unconfiguredComponents.length === 0}
          >
            {t('settings.blockRules.addRule')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.blockRules.description')}
        </p>
        {blockRulesError && <div className="error" role="alert">{blockRulesError}</div>}
      </div>

      {showBlockRuleForm && (
        <div className="card">
          <h3>{editingBlockRuleComponent ? t('settings.blockRules.editRule') : t('settings.blockRules.newRule')}</h3>
          <form onSubmit={handleSubmitBlockRule}>
            <div className="form-group">
              <label>{t('settings.blockRules.fields.component')}</label>
              {editingBlockRuleComponent ? (
                <input type="text" value={blockRuleForm.component} disabled />
              ) : (
                <select
                  value={blockRuleForm.component}
                  onChange={(e) => setBlockRuleForm({ ...blockRuleForm, component: e.target.value })}
                  required
                >
                  {unconfiguredComponents.map((c) => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              )}
            </div>
            <div className="form-group">
              <label>{t('settings.blockRules.fields.preferredBlockSize')}</label>
              <select
                value={blockRuleForm.preferredBlockSize}
                onChange={(e) => setBlockRuleForm({ ...blockRuleForm, preferredBlockSize: parseInt(e.target.value, 10) })}
              >
                {BLOCK_SIZES.map((size) => (
                  <option key={size} value={size}>{size}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('settings.blockRules.sizeHint')}
              </div>
            </div>
            <div className="form-group">
              <label>{t('settings.blockRules.fields.maxBlocksPerDay')}</label>
              <select
                value={blockRuleForm.maxBlocksPerDay}
                onChange={(e) => setBlockRuleForm({ ...blockRuleForm, maxBlocksPerDay: parseInt(e.target.value, 10) })}
              >
                {BLOCK_SIZES.map((size) => (
                  <option key={size} value={size}>{size}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('settings.blockRules.maxBlocksPerDayHint')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingBlockRule || !blockRuleForm.component}>
                {savingBlockRule ? t('settings.blockRules.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelBlockRule}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {blockRules.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.blockRules.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.blockRules.table.component')}</th>
                <th>{t('settings.blockRules.table.preferredBlockSize')}</th>
                <th>{t('settings.blockRules.table.maxBlocksPerDay')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {blockRules.map((rule) => (
                <tr key={rule.component}>
                  <td>{rule.component}</td>
                  <td>{rule.preferredBlockSize}h</td>
                  <td>{rule.maxBlocksPerDay}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditBlockRule(rule)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteBlockRule(rule.component)}>
                      {t('settings.blockRules.resetToDefault')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'constraintWeights' && (
      <div role="tabpanel" id="settings-panel-constraintWeights" aria-labelledby="settings-tab-constraintWeights">
      <div className="card">
        <h3>{t('settings.constraintWeights.title')}</h3>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.constraintWeights.description')}
        </p>
        {constraintWeightsError && <div className="error" role="alert">{constraintWeightsError}</div>}
      </div>

      <div className="card">
        {constraintWeights.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.constraintWeights.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.constraintWeights.table.constraint')}</th>
                <th>{t('settings.constraintWeights.table.defaultWeight')}</th>
                <th>{t('settings.constraintWeights.table.currentWeight')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {constraintWeights.map((row) => {
                const draft = weightDrafts[row.constraintName] ?? row.effectiveWeight;
                const isOverridden = row.overrideWeight !== null && row.overrideWeight !== undefined;
                const isDirty = String(draft) !== String(row.effectiveWeight);
                const isSaving = savingWeightFor === row.constraintName;
                return (
                  <tr key={row.constraintName}>
                    <td>
                      {row.constraintName}
                      {isOverridden && (
                        <span style={{ marginLeft: '8px', fontSize: '11px', color: 'var(--color-warning)' }}>
                          {t('settings.constraintWeights.overridden')}
                        </span>
                      )}
                    </td>
                    <td>{row.defaultWeight}</td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        max={1000}
                        value={draft}
                        onChange={(e) => handleWeightDraftChange(row.constraintName, e.target.value)}
                        style={{ width: '80px' }}
                      />
                    </td>
                    <td>
                      <button
                        className="btn btn-primary"
                        onClick={() => handleSaveWeight(row.constraintName)}
                        disabled={!isDirty || isSaving}
                        style={{ marginRight: '5px' }}
                      >
                        {isSaving ? t('settings.constraintWeights.saving') : t('common.save')}
                      </button>
                      <button
                        className="btn btn-danger"
                        onClick={() => handleResetWeight(row.constraintName)}
                        disabled={!isOverridden || isSaving}
                      >
                        {t('settings.blockRules.resetToDefault')}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'semesterHourLimits' && (
      <div role="tabpanel" id="settings-panel-semesterHourLimits" aria-labelledby="settings-tab-semesterHourLimits">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.semesterHourLimits.title')}</h3>
          <button className="btn btn-success" onClick={handleAddSemesterHourLimit}>
            {t('settings.semesterHourLimits.addLimit')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.semesterHourLimits.description')}
        </p>
        {semesterHourLimitsError && <div className="error" role="alert">{semesterHourLimitsError}</div>}
      </div>

      {showSemesterHourLimitForm && (
        <div className="card">
          <h3>{editingSemester !== null ? t('settings.semesterHourLimits.editLimit') : t('settings.semesterHourLimits.newLimit')}</h3>
          <form onSubmit={handleSubmitSemesterHourLimit}>
            <div className="form-group">
              <label>{t('settings.semesterHourLimits.fields.semester')}</label>
              <input
                type="number"
                min={1}
                max={12}
                value={semesterHourLimitForm.semester}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, semester: e.target.value })}
                disabled={editingSemester !== null}
                required
              />
            </div>
            <div className="form-group">
              <label>{t('settings.semesterHourLimits.fields.latestEndHour')}</label>
              <select
                value={semesterHourLimitForm.latestEndHour}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, latestEndHour: parseInt(e.target.value, 10) })}
              >
                {START_HOURS.map((hour) => (
                  <option key={hour} value={hour}>{formatHour(hour)}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>{t('settings.semesterHourLimits.fields.severity')}</label>
              <select
                value={semesterHourLimitForm.severity}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, severity: e.target.value })}
              >
                {SEVERITY_OPTIONS.map((severity) => (
                  <option key={severity} value={severity}>{t(`settings.semesterHourLimits.severities.${severity}`)}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {semesterHourLimitForm.severity === 'HARD'
                  ? t('settings.semesterHourLimits.hardHint')
                  : t('settings.semesterHourLimits.softHint')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingSemesterHourLimit || !semesterHourLimitForm.semester}>
                {savingSemesterHourLimit ? t('settings.semesterHourLimits.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelSemesterHourLimit}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {semesterHourLimits.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.semesterHourLimits.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.semesterHourLimits.table.semester')}</th>
                <th>{t('settings.semesterHourLimits.table.latestEndHour')}</th>
                <th>{t('settings.semesterHourLimits.table.severity')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {semesterHourLimits.map((limit) => (
                <tr key={limit.semester}>
                  <td>{limit.semester}</td>
                  <td>{formatHour(limit.latestEndHour)}</td>
                  <td>{t(`settings.semesterHourLimits.severities.${limit.severity}`)}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditSemesterHourLimit(limit)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteSemesterHourLimit(limit.semester)}>
                      {t('common.delete')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'calendar' && (
      <div role="tabpanel" id="settings-panel-calendar" aria-labelledby="settings-tab-calendar">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.calendar.title')}</h3>
          <button className="btn btn-success" onClick={handleAddCalendarException}>
            {t('settings.calendar.addException')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.calendar.description')}
        </p>
        {calendarExceptionsError && <div className="error" role="alert">{calendarExceptionsError}</div>}
      </div>

      {showCalendarExceptionForm && (
        <div className="card">
          <h3>{editingCalendarExceptionDate ? t('settings.calendar.editException') : t('settings.calendar.newException')}</h3>
          <form onSubmit={handleSubmitCalendarException}>
            <div className="form-group">
              <label>{t('settings.calendar.fields.date')}</label>
              {editingCalendarExceptionDate ? (
                <input type="text" value={calendarExceptionForm.date} disabled />
              ) : (
                <input
                  type="date"
                  value={calendarExceptionForm.date}
                  onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, date: e.target.value })}
                  required
                />
              )}
            </div>
            <div className="form-group">
              <label>{t('settings.calendar.fields.type')}</label>
              <select
                value={calendarExceptionForm.type}
                onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, type: e.target.value })}
              >
                {CALENDAR_EXCEPTION_TYPES.map((type) => (
                  <option key={type} value={type}>{t(`settings.calendar.types.${type}`)}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>{t('settings.calendar.fields.label')}</label>
              <input
                type="text"
                value={calendarExceptionForm.label}
                onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, label: e.target.value })}
                maxLength={200}
              />
            </div>
            {calendarExceptionForm.type === 'HALF_DAY' && (
              <div className="form-group">
                <label>{t('settings.calendar.fields.endHour')}</label>
                <select
                  value={calendarExceptionForm.endHour}
                  onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, endHour: parseInt(e.target.value, 10) })}
                >
                  {START_HOURS.map((h) => (
                    <option key={h} value={h}>{formatHour(h)}</option>
                  ))}
                </select>
              </div>
            )}
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingCalendarException || !calendarExceptionForm.date}>
                {savingCalendarException ? t('settings.calendar.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelCalendarException}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {calendarExceptions.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.calendar.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.calendar.table.date')}</th>
                <th>{t('settings.calendar.table.type')}</th>
                <th>{t('settings.calendar.table.label')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {calendarExceptions.map((exception) => (
                <tr key={exception.exceptionDate}>
                  <td>{exception.exceptionDate}{exception.type === 'HALF_DAY' && exception.endHour ? ` (${formatHour(exception.endHour)})` : ''}</td>
                  <td>{t(`settings.calendar.types.${exception.type}`)}</td>
                  <td>{exception.label || '-'}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditCalendarException(exception)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteCalendarException(exception.exceptionDate)}>
                      {t('common.delete')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'timeslots' && (
      <div role="tabpanel" id="settings-panel-timeslots" aria-labelledby="settings-tab-timeslots">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.timeslots.title')}</h3>
          <button className="btn btn-success" onClick={handleAdd}>
            {t('settings.timeslots.addTimeslot')}
          </button>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTimeslot ? t('settings.timeslots.editTimeslot') : t('settings.timeslots.newTimeslot')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.day')}</label>
              <select name="dayOfWeek" value={form.dayOfWeek} onChange={handleField}>
                {DAY_LABELS.map((day) => (
                  <option key={day.value} value={day.value}>{t(`common.days.${day.key}`)}</option>
                ))}
              </select>
              {fieldErrors.dayOfWeek && <div className="error" role="alert">{fieldErrors.dayOfWeek}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.startHour')}</label>
              <select name="startHour" value={form.startHour} onChange={handleField}>
                {START_HOURS.map((h) => (
                  <option key={h} value={h}>{formatHour(h)}</option>
                ))}
              </select>
              {fieldErrors.startHour && <div className="error" role="alert">{fieldErrors.startHour}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.length')}</label>
              <select name="lengthHours" value={form.lengthHours} onChange={handleField}>
                {LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.lengthHours && <div className="error" role="alert">{fieldErrors.lengthHours}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.endsAt')}</label>
              <span style={{ color: exceedsDayBounds ? 'var(--color-danger-dark)' : undefined }}>
                {formatHour(endHour)}{exceedsDayBounds ? t('settings.timeslots.exceedsDayBounds') : ''}
              </span>
              {fieldErrors.withinDayBounds && <div className="error" role="alert">{fieldErrors.withinDayBounds}</div>}
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={exceedsDayBounds}>{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      {timeslots.length === 0 ? (
        <div className="card">
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.timeslots.none')}</p>
        </div>
      ) : (
        DAY_LABELS.map((day) => {
          const dayTimeslots = timeslots
            .filter((ts) => ts.dayOfWeek === day.value)
            .sort((a, b) => a.startHour - b.startHour);
          if (dayTimeslots.length === 0) return null;
          return (
            <div className="card" key={day.value}>
              <h4 style={{ marginBottom: '10px', color: 'var(--color-ink)' }}>{t(`common.days.${day.key}`)}</h4>
              <table>
                <thead>
                  <tr>
                    <th>{t('settings.timeslots.table.start')}</th>
                    <th>{t('settings.timeslots.table.end')}</th>
                    <th>{t('settings.timeslots.table.length')}</th>
                    <th>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dayTimeslots.map((timeslot) => (
                    <tr key={timeslot.id}>
                      <td>{formatHour(timeslot.startHour)}</td>
                      <td>{formatHour(timeslot.startHour + timeslot.lengthHours)}</td>
                      <td>{timeslot.lengthHours}</td>
                      <td>
                        <button className="btn btn-primary" onClick={() => handleEdit(timeslot)} style={{ marginRight: '5px' }}>
                          {t('common.edit')}
                        </button>
                        <button className="btn btn-danger" onClick={() => handleDelete(timeslot)}>
                          {t('common.delete')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        })
      )}
      </div>
      )}

      {activeTab === 'databaseBackups' && (
      <div role="tabpanel" id="settings-panel-databaseBackups" aria-labelledby="settings-tab-databaseBackups">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <h3>{t('settings.databaseBackups.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleExportDatabase}
            disabled={dbStatus?.state === 'RUNNING'}
          >
            {dbStatus?.state === 'RUNNING' ? t('settings.databaseBackups.running') : `⇩ ${t('settings.databaseBackups.exportButton')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.databaseBackups.description')}
        </p>
        {dbError && <div className="error" role="alert">{dbError}</div>}
        {dbStatus && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('settings.databaseBackups.lastOperation')}</strong> {dbStatus.lastOperation ?? '-'}</span>
              <span><strong>{t('settings.solver.state')}</strong> {dbStatus.state}</span>
              <span><strong>{t('settings.solver.started')}</strong> {formatTimestamp(dbStatus.startedAt)}</span>
              <span><strong>{t('settings.solver.finished')}</strong> {formatTimestamp(dbStatus.finishedAt)}</span>
              <span><strong>{t('settings.solver.exitCode')}</strong> {dbStatus.exitCode ?? '-'}</span>
            </div>
            {dbStatus.log && dbStatus.log.length > 0 && (
              <pre
                style={{
                  marginTop: '10px',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: '10px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {dbStatus.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.databaseBackups.filesTitle')}</h3>
          <button className="btn btn-secondary" onClick={loadDatabaseBackups}>↻ {t('settings.complianceSnapshots.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.databaseBackups.filesDescription')}
        </p>
        {dbBackupsError && <div className="error" role="alert">{dbBackupsError}</div>}
        {dbBackups.length === 0 && !dbBackupsError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.databaseBackups.none')}</p>
        )}
        {dbBackups.length > 0 && (
          <table style={{ marginTop: '8px' }}>
            <thead>
              <tr>
                <th>{t('settings.databaseBackups.table.filename')}</th>
                <th>{t('settings.databaseBackups.table.size')}</th>
                <th>{t('settings.databaseBackups.table.modified')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {dbBackups.map((f) => (
                <tr key={f.filename}>
                  <td>{f.filename}</td>
                  <td>{formatBytes(f.sizeBytes)}</td>
                  <td>{formatTimestamp(f.modifiedAt)}</td>
                  <td style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn btn-primary"
                      onClick={() => handleDownloadBackup(f.filename)}
                      disabled={downloadingBackup === f.filename}
                    >
                      {downloadingBackup === f.filename ? t('settings.complianceSnapshots.opening') : t('settings.databaseBackups.download')}
                    </button>
                    <button
                      className="btn btn-danger"
                      onClick={() => handleImportDatabase(f.filename)}
                      disabled={dbStatus?.state === 'RUNNING'}
                    >
                      {t('settings.databaseBackups.restore')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      </div>
      )}

      {activeTab === 'auditLog' && (
      <div role="tabpanel" id="settings-panel-auditLog" aria-labelledby="settings-tab-auditLog">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.auditLog.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAuditLog}>↻ {t('settings.auditLog.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.auditLog.description')}
        </p>
        {auditLogError && <div className="error" role="alert">{auditLogError}</div>}
        {auditLog.length === 0 && !auditLogError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.auditLog.none')}</p>
        )}
        {auditLog.length > 0 && (
          <>
            <table style={{ marginTop: '8px' }}>
              <thead>
                <tr>
                  <th>{t('settings.auditLog.table.when')}</th>
                  <th>{t('settings.auditLog.table.user')}</th>
                  <th>{t('settings.auditLog.table.method')}</th>
                  <th>{t('settings.auditLog.table.path')}</th>
                  <th>{t('settings.auditLog.table.status')}</th>
                </tr>
              </thead>
              <tbody>
                {auditLogPagination.pageItems.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatTimestamp(entry.occurredAt)}</td>
                    <td>{entry.username}</td>
                    <td>{entry.httpMethod}</td>
                    <td>{entry.path}</td>
                    <td>{entry.statusCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={auditLogPagination.page}
              pageCount={auditLogPagination.pageCount}
              totalItems={auditLogPagination.totalItems}
              pageSize={DEFAULT_PAGE_SIZE}
              onPageChange={auditLogPagination.setPage}
            />
          </>
        )}
      </div>
      </div>
      )}
    </div>
  );
}

export default Settings;
