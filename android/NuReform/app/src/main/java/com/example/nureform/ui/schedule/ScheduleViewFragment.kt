package com.example.nureform.ui.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.R
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.data.model.UserRole
import com.example.nureform.databinding.FragmentScheduleViewBinding
import com.example.nureform.ui.BaseFragment
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleViewFragment : BaseFragment<FragmentScheduleViewBinding>(
    FragmentScheduleViewBinding::inflate
) {

    private val viewModel: ScheduleViewModel by viewModel()
    private val args: ScheduleViewFragmentArgs by navArgs()

    private var isManager = false
    private var isEditMode = false
    private var editableSchedules: MutableList<NurseSchedule> = mutableListOf()

    // Day index -> NurseSchedule property name (used for edit dialog)
    private val dayKeys = listOf("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday")

    private lateinit var scheduleAdapter: ScheduleAdapter

    private var currentSchedules: List<NurseSchedule> = emptyList()
    private var currentWeekSchedules: List<NurseSchedule> = emptyList()
    private var nextWeekSchedules: List<NurseSchedule> = emptyList()
    private var currentWeekInfoStr: String = ""
    private var nextWeekInfoStr: String = ""
    private var currentWeekInfo: String = ""
    private var currentTitle: String = ""
    private var selectedTab = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkManagerRole()
        setupTabs()
        setupObservers()
        setupClickListeners()
        viewModel.loadSchedule(args.showAllNurses, args.nurseName)
    }

    private fun checkManagerRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val role = doc.getString("role")
                isManager = role == UserRole.MANAGER.value
                if (isManager) {
                    binding.btnUpdate.visibility = View.GONE
                    binding.btnCancelEdit.visibility = View.GONE
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildAdapter(): ScheduleAdapter {
        val cellClickHandler: ((Int, Int) -> Unit)? = if (isEditMode && isManager) {
            { rowIndex, dayIndex -> showEditCellDialog(rowIndex, dayIndex) }
        } else null
        return ScheduleAdapter(onCellClick = cellClickHandler)
    }

    private fun setupRecyclerView() {
        scheduleAdapter = buildAdapter()
        binding.rvSchedule.apply {
            adapter = scheduleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                // Exit edit mode when switching tabs
                if (isEditMode) exitEditMode()
                updateDisplayForSelectedTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentWeekState.collect { state ->
                if (state is ScheduleViewState.Success) currentWeekSchedules = state.schedules
                updateDisplayForSelectedTab()
                updateDownloadButtonVisibility()
            }
        }

        lifecycleScope.launch {
            viewModel.nextWeekState.collect { state ->
                if (state is ScheduleViewState.Success) nextWeekSchedules = state.schedules
                updateDisplayForSelectedTab()
                updateDownloadButtonVisibility()
            }
        }

        lifecycleScope.launch {
            viewModel.title.collect { title ->
                binding.tvTitle.text = title
                currentTitle = title
            }
        }

        lifecycleScope.launch {
            viewModel.currentWeekLabel.collect { label ->
                currentWeekInfoStr = label
                binding.tabLayout.getTabAt(0)?.text = "השבוע ($label)"
                if (selectedTab == 0) {
                    binding.tvWeekInfo.text = label
                    currentWeekInfo = label
                }
            }
        }

        lifecycleScope.launch {
            viewModel.nextWeekLabel.collect { label ->
                nextWeekInfoStr = label
                binding.tabLayout.getTabAt(1)?.text = "שבוע הבא ($label)"
                if (selectedTab == 1) {
                    binding.tvWeekInfo.text = label
                    currentWeekInfo = label
                }
            }
        }

        lifecycleScope.launch {
            viewModel.editState.collect { state ->
                when (state) {
                    is EditScheduleState.Loading -> showLoading(true)
                    is EditScheduleState.Success -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), getString(R.string.schedule_updated), Toast.LENGTH_SHORT).show()
                        exitEditMode()
                        viewModel.loadSchedule(args.showAllNurses, args.nurseName)
                    }
                    is EditScheduleState.Error -> {
                        showLoading(false)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> showLoading(false)
                }
            }
        }
    }

    private fun updateDownloadButtonVisibility() {
        val hasAnyData = currentWeekSchedules.isNotEmpty() || nextWeekSchedules.isNotEmpty()
        binding.btnDownloadPdf.visibility = if (hasAnyData) View.VISIBLE else View.GONE
    }

    private fun updateTableHeaders(weekDocId: String) {
        if (weekDocId.isBlank()) return
        val parts = weekDocId.split("_")
        if (parts.size != 2) return
        val year = parts[0].toIntOrNull() ?: return
        val week = parts[1].toIntOrNull() ?: return

        val cal = Calendar.getInstance()
        cal.clear()
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.minimalDaysInFirstWeek = 1
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.WEEK_OF_YEAR, week)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        val dayNames = listOf("יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "יום שבת")
        val headerViews = listOf(
            binding.tvHeaderSunday, binding.tvHeaderMonday, binding.tvHeaderTuesday,
            binding.tvHeaderWednesday, binding.tvHeaderThursday, binding.tvHeaderFriday, binding.tvHeaderSaturday
        )
        headerViews.forEachIndexed { i, tv ->
            tv.text = "${dayNames[i]}\n${fmt.format(cal.time)}"
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun updateDisplayForSelectedTab() {
        val state = if (selectedTab == 0) viewModel.currentWeekState.value else viewModel.nextWeekState.value
        val weekDocId = if (selectedTab == 0) viewModel.currentWeekDocId else viewModel.nextWeekDocId

        currentWeekInfo = if (selectedTab == 0) viewModel.currentWeekLabel.value else viewModel.nextWeekLabel.value
        binding.tvWeekInfo.text = currentWeekInfo
        updateTableHeaders(weekDocId)

        when (state) {
            is ScheduleViewState.Idle -> { showLoading(false); hideEmptyState(); hideRunningState() }
            is ScheduleViewState.Loading -> { showLoading(true); hideEmptyState(); hideRunningState() }
            is ScheduleViewState.Success -> {
                showLoading(false); hideEmptyState(); hideRunningState()
                currentSchedules = state.schedules
                setupRecyclerView()
                scheduleAdapter.submitList(state.schedules.toList())
                binding.scheduleScrollView.visibility = View.VISIBLE
                if (isManager && !isEditMode) binding.btnEditSchedule.visibility = View.VISIBLE
            }
            is ScheduleViewState.Empty -> {
                showLoading(false); showEmptyState(); hideRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
            is ScheduleViewState.Running -> {
                showLoading(false); hideEmptyState(); showRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
            is ScheduleViewState.Error -> {
                showLoading(false); showEmptyState(); hideRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnDownloadPdf.setOnClickListener { generateAndSavePdf() }

        binding.btnEditSchedule.setOnClickListener { enterEditMode() }

        binding.btnUpdate.setOnClickListener {
            val weekDocId = if (selectedTab == 0) viewModel.currentWeekDocId else viewModel.nextWeekDocId
            viewModel.saveEditedSchedule(weekDocId, editableSchedules.toList())
        }

        binding.btnCancelEdit.setOnClickListener { findNavController().navigateUp() }
    }

    // ── Edit mode ─────────────────────────────────────────────────────────────

    private fun enterEditMode() {
        isEditMode = true
        editableSchedules = currentSchedules.toMutableList()
        binding.btnEditSchedule.visibility = View.GONE
        binding.btnUpdate.visibility = View.VISIBLE
        binding.btnCancelEdit.visibility = View.VISIBLE
        binding.btnDownloadPdf.visibility = View.GONE
        binding.tvTitle.text = "${binding.tvTitle.text} *"
        setupRecyclerView()
        scheduleAdapter.submitList(editableSchedules.toList())
    }

    private fun exitEditMode() {
        isEditMode = false
        editableSchedules.clear()
        binding.btnUpdate.visibility = View.GONE
        binding.btnCancelEdit.visibility = View.GONE
        val cleaned = binding.tvTitle.text.toString().removeSuffix(" *")
        binding.tvTitle.text = cleaned
        updateDownloadButtonVisibility()
        // Show edit button again if data is available and user is manager
        if (isManager && currentSchedules.isNotEmpty()) {
            binding.btnEditSchedule.visibility = View.VISIBLE
        }
        setupRecyclerView()
        scheduleAdapter.submitList(currentSchedules.toList())
    }

    private fun showEditCellDialog(rowIndex: Int, dayIndex: Int) {
        val shiftOptions = arrayOf("בוקר", "צהריים", "ערב", getString(R.string.no_shift))
        val hebrewToKey = mapOf("בוקר" to "בוקר", "צהריים" to "צהריים", "ערב" to "ערב", getString(R.string.no_shift) to "X")

        val current = getCellValue(editableSchedules[rowIndex], dayIndex)
        val currentIndex = shiftOptions.indexOfFirst { it == current || (current == "X" && it == getString(R.string.no_shift)) }

        AlertDialog.Builder(requireContext())
            .setTitle(editableSchedules[rowIndex].nurseName)
            .setSingleChoiceItems(shiftOptions, currentIndex) { dialog, which ->
                val newValue = hebrewToKey[shiftOptions[which]] ?: "X"
                editableSchedules[rowIndex] = setCellValue(editableSchedules[rowIndex], dayIndex, newValue)
                scheduleAdapter.submitList(editableSchedules.toList())
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun getCellValue(schedule: NurseSchedule, dayIndex: Int): String = when (dayIndex) {
        0 -> schedule.sunday
        1 -> schedule.monday
        2 -> schedule.tuesday
        3 -> schedule.wednesday
        4 -> schedule.thursday
        5 -> schedule.friday
        6 -> schedule.saturday
        else -> "X"
    }

    private fun setCellValue(schedule: NurseSchedule, dayIndex: Int, value: String): NurseSchedule = when (dayIndex) {
        0 -> schedule.copy(sunday = value)
        1 -> schedule.copy(monday = value)
        2 -> schedule.copy(tuesday = value)
        3 -> schedule.copy(wednesday = value)
        4 -> schedule.copy(thursday = value)
        5 -> schedule.copy(friday = value)
        6 -> schedule.copy(saturday = value)
        else -> schedule
    }

    // ── PDF generation ────────────────────────────────────────────────────────

    private fun generateAndSavePdf() {
        if (currentWeekSchedules.isEmpty() && nextWeekSchedules.isEmpty()) return

        try {
            // RTL canvas order: שבת leftmost → שם rightmost (matches Hebrew reading direction)
            val dayHeaders = listOf("שבת", "שישי", "חמישי", "רביעי", "שלישי", "שני", "ראשון", "משרה%", "שם")
            val colWidths = listOf(80f, 80f, 80f, 80f, 80f, 80f, 80f, 60f, 130f)
            val totalWidth = colWidths.sum() + 40f
            val rowHeight = 35f
            val headerHeight = 60f
            val reportHeaderHeight = 100f
            val startX = 20f

            val document = PdfDocument()
            var pageNumber = 1

            pageNumber = drawSchedulePage(
                document, pageNumber, currentWeekSchedules,
                "$currentTitle - $currentWeekInfoStr (השבוע)",
                currentWeekInfoStr,
                "אין שיבוץ לשבוע הנוכחי",
                dayHeaders, colWidths, totalWidth, rowHeight, headerHeight, reportHeaderHeight, startX
            )
            drawSchedulePage(
                document, pageNumber, nextWeekSchedules,
                "$currentTitle - $nextWeekInfoStr (שבוע הבא)",
                nextWeekInfoStr,
                "אין שיבוץ לשבוע הבא",
                dayHeaders, colWidths, totalWidth, rowHeight, headerHeight, reportHeaderHeight, startX
            )

            val fileName = "schedule_${currentWeekInfoStr.replace(" ", "_").replace(",", "")}.pdf"
            val savedUri = savePdfToDownloads(document, fileName)
            document.close()

            Toast.makeText(requireContext(), getString(R.string.pdf_saved), Toast.LENGTH_SHORT).show()
            showPdfNotification(fileName, savedUri)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), getString(R.string.pdf_error), Toast.LENGTH_SHORT).show()
        }
    }

    /** Compute Sun–Sat ISO dates for the week described by a "YYYY_W" doc ID. */
    private fun weekDates(weekLabel: String): List<String> {
        // weekLabel is like "שבוע 17, 2026" — parse week/year from viewModel doc IDs instead
        // We use the actual Calendar week-math matching ShiftsRepository
        return emptyList() // placeholder; dates are embedded in dayHeadersWithDates below
    }

    private fun dayHeadersWithDates(weekDocId: String): List<String> {
        val parts = weekDocId.split("_")
        if (parts.size != 2) return listOf("שבת", "שישי", "חמישי", "רביעי", "שלישי", "שני", "ראשון", "משרה%", "שם")
        val year = parts[0].toIntOrNull() ?: return emptyList()
        val week = parts[1].toIntOrNull() ?: return emptyList()

        val cal = Calendar.getInstance()
        cal.clear()
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.minimalDaysInFirstWeek = 1
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.WEEK_OF_YEAR, week)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val fmt = SimpleDateFormat("dd/MM", Locale.getDefault())
        val hebrewNames = listOf("ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")

        // Build Sun→Sat date strings, then reverse for RTL canvas (שבת first, שם last)
        val dayDateHeaders = (0..6).map { i ->
            val label = "${hebrewNames[i]}\n${fmt.format(cal.time)}"
            cal.add(Calendar.DAY_OF_YEAR, 1)
            label
        }
        return dayDateHeaders.reversed() + listOf("משרה%", "שם")
    }

    private fun weekDateRange(weekDocId: String): String {
        val parts = weekDocId.split("_")
        if (parts.size != 2) return ""
        val year = parts[0].toIntOrNull() ?: return ""
        val week = parts[1].toIntOrNull() ?: return ""

        val cal = Calendar.getInstance()
        cal.clear()
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.minimalDaysInFirstWeek = 1
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.WEEK_OF_YEAR, week)
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val start = fmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val end = fmt.format(cal.time)
        return "$start – $end"
    }

    private fun drawSchedulePage(
        document: PdfDocument,
        pageNumber: Int,
        schedules: List<NurseSchedule>,
        pageTitle: String,
        weekLabel: String,
        emptyMessage: String,
        dayHeaders: List<String>,
        colWidths: List<Float>,
        totalWidth: Float,
        rowHeight: Float,
        headerHeight: Float,
        reportHeaderHeight: Float,
        startX: Float
    ): Int {
        val weekDocId = if (selectedTab == 0) viewModel.currentWeekDocId else viewModel.nextWeekDocId
        val computedHeaders = dayHeadersWithDates(weekDocId)
        val dateRange = weekDateRange(weekDocId)
        val generatedOn = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val dataRows = schedules.size.coerceAtLeast(1)
        val totalHeight = reportHeaderHeight + headerHeight + (dataRows * rowHeight) + 60f

        val pageInfo = PdfDocument.PageInfo.Builder(totalWidth.toInt(), totalHeight.toInt(), pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val subPaint = Paint().apply { color = Color.DKGRAY; textSize = 11f; textAlign = Paint.Align.RIGHT }
        val headerPaint = Paint().apply { color = Color.WHITE; textSize = 10f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val cellPaint = Paint().apply { color = Color.BLACK; textSize = 11f; textAlign = Paint.Align.CENTER }
        val headerBgPaint = Paint().apply { color = Color.rgb(25, 50, 100) }
        val rowBgPaint = Paint().apply { color = Color.rgb(240, 240, 245) }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val emptyPaint = Paint().apply { color = Color.GRAY; textSize = 14f; textAlign = Paint.Align.CENTER }

        // Report header block
        canvas.drawText(pageTitle, totalWidth - 20f, 22f, titlePaint)
        if (dateRange.isNotEmpty()) canvas.drawText("תאריכים: $dateRange", totalWidth - 20f, 40f, subPaint)
        canvas.drawText("נוצר ב: $generatedOn", totalWidth - 20f, 56f, subPaint)
        canvas.drawText("סה\"כ אחיות: ${schedules.size}", totalWidth - 20f, 72f, subPaint)

        if (schedules.isEmpty()) {
            canvas.drawText(emptyMessage, totalWidth / 2, reportHeaderHeight + 40f, emptyPaint)
            document.finishPage(page)
            return pageNumber + 1
        }

        // Header row (RTL: rightmost = שם, then mishre%, then days left to right)
        val headerY = reportHeaderHeight
        canvas.drawRect(startX, headerY, totalWidth - 20f, headerY + headerHeight, headerBgPaint)

        var x = startX
        val headers = if (computedHeaders.size == dayHeaders.size) computedHeaders else dayHeaders
        for (i in headers.indices) {
            val lines = headers[i].split("\n")
            if (lines.size == 2) {
                canvas.drawText(lines[0], x + colWidths[i] / 2, headerY + 22f, headerPaint)
                canvas.drawText(lines[1], x + colWidths[i] / 2, headerY + 40f, headerPaint)
            } else {
                canvas.drawText(headers[i], x + colWidths[i] / 2, headerY + headerHeight / 2 + 4f, headerPaint)
            }
            x += colWidths[i]
        }

        // Data rows
        for (row in schedules.indices) {
            val schedule = schedules[row]
            val rowY = reportHeaderHeight + headerHeight + (row * rowHeight)

            if (row % 2 == 0) canvas.drawRect(startX, rowY, totalWidth - 20f, rowY + rowHeight, rowBgPaint)
            canvas.drawLine(startX, rowY + rowHeight, totalWidth - 20f, rowY + rowHeight, linePaint)

            val values = listOf(
                schedule.saturday, schedule.friday, schedule.thursday,
                schedule.wednesday, schedule.tuesday, schedule.monday, schedule.sunday,
                "${schedule.jobPercentage}%", schedule.nurseName
            )

            x = startX
            for (i in values.indices) {
                val paint = if (i == values.size - 1) Paint(cellPaint).apply { isFakeBoldText = true } else cellPaint
                canvas.drawText(values[i], x + colWidths[i] / 2, rowY + 22f, paint)
                x += colWidths[i]
            }
        }

        document.finishPage(page)
        return pageNumber + 1
    }

    private fun savePdfToDownloads(document: PdfDocument, fileName: String): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create file")
            requireContext().contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw Exception("Failed to open output stream")
            return uri
        } else {
            @Suppress("DEPRECATION")
            val file = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            file.outputStream().use { document.writeTo(it) }
            return Uri.fromFile(file)
        }
    }

    private fun showPdfNotification(fileName: String, fileUri: Uri) {
        val channelId = "pdf_download"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "PDF Downloads", NotificationManager.IMPORTANCE_DEFAULT)
            requireContext().getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(requireContext(), 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("PDF נשמר")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(requireContext()).notify(1001, notification)
        } catch (_: SecurityException) {}
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() { binding.tvEmptyState.visibility = View.VISIBLE }
    private fun hideEmptyState() { binding.tvEmptyState.visibility = View.GONE }
    private fun showRunningState() { binding.tvRunningState.visibility = View.VISIBLE }
    private fun hideRunningState() { binding.tvRunningState.visibility = View.GONE }
}
