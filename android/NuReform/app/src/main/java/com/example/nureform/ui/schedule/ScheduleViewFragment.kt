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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.nureform.R
import com.example.nureform.data.model.NurseSchedule
import com.example.nureform.databinding.FragmentScheduleViewBinding
import com.example.nureform.ui.BaseFragment
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.OutputStream

class ScheduleViewFragment : BaseFragment<FragmentScheduleViewBinding>(
    FragmentScheduleViewBinding::inflate
) {

    private val viewModel: ScheduleViewModel by viewModel()
    private val args: ScheduleViewFragmentArgs by navArgs()
    private val scheduleAdapter = ScheduleAdapter()
    private var currentSchedules: List<NurseSchedule> = emptyList()
    private var currentWeekSchedules: List<NurseSchedule> = emptyList()
    private var nextWeekSchedules: List<NurseSchedule> = emptyList()
    private var currentWeekInfoStr: String = ""
    private var nextWeekInfoStr: String = ""
    private var currentWeekInfo: String = ""
    private var currentTitle: String = ""
    private var selectedTab = 0 // 0 = current week, 1 = next week

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabs()
        setupObservers()
        setupClickListeners()
        viewModel.loadSchedule(args.showAllNurses, args.nurseName)
    }

    private fun setupRecyclerView() {
        binding.rvSchedule.apply {
            adapter = scheduleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedTab = tab?.position ?: 0
                updateDisplayForSelectedTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            viewModel.currentWeekState.collect { state ->
                if (state is ScheduleViewState.Success) {
                    currentWeekSchedules = state.schedules
                }
                updateDisplayForSelectedTab()
                updateDownloadButtonVisibility()
            }
        }

        lifecycleScope.launch {
            viewModel.nextWeekState.collect { state ->
                if (state is ScheduleViewState.Success) {
                    nextWeekSchedules = state.schedules
                }
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
    }

    private fun updateDownloadButtonVisibility() {
        val hasAnyData = currentWeekSchedules.isNotEmpty() || nextWeekSchedules.isNotEmpty()
        binding.btnDownloadPdf.visibility = if (hasAnyData) View.VISIBLE else View.GONE
    }

    private fun updateDisplayForSelectedTab() {
        val state = if (selectedTab == 0) {
            viewModel.currentWeekState.value
        } else {
            viewModel.nextWeekState.value
        }

        currentWeekInfo = if (selectedTab == 0) {
            viewModel.currentWeekLabel.value
        } else {
            viewModel.nextWeekLabel.value
        }
        binding.tvWeekInfo.text = currentWeekInfo

        when (state) {
            is ScheduleViewState.Idle -> {
                showLoading(false)
                hideEmptyState()
                hideRunningState()
            }
            is ScheduleViewState.Loading -> {
                showLoading(true)
                hideEmptyState()
                hideRunningState()
            }
            is ScheduleViewState.Success -> {
                showLoading(false)
                hideEmptyState()
                hideRunningState()
                currentSchedules = state.schedules
                scheduleAdapter.submitList(state.schedules)
                binding.scheduleScrollView.visibility = View.VISIBLE
            }
            is ScheduleViewState.Empty -> {
                showLoading(false)
                showEmptyState()
                hideRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
            is ScheduleViewState.Running -> {
                showLoading(false)
                hideEmptyState()
                showRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
            is ScheduleViewState.Error -> {
                showLoading(false)
                showEmptyState()
                hideRunningState()
                binding.scheduleScrollView.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnDownloadPdf.setOnClickListener {
            generateAndSavePdf()
        }
    }

    private fun generateAndSavePdf() {
        if (currentWeekSchedules.isEmpty() && nextWeekSchedules.isEmpty()) return

        try {
            val dayHeaders = listOf("שם", "ראשון", "שני", "שלישי", "רביעי", "חמישי", "שישי", "שבת")
            val colWidths = listOf(120f, 80f, 80f, 80f, 80f, 80f, 80f, 80f)
            val totalWidth = colWidths.sum() + 40f
            val rowHeight = 35f
            val headerHeight = 45f
            val titleHeight = 80f
            val startX = 20f

            val document = PdfDocument()
            var pageNumber = 1

            // Page 1: Current week
            pageNumber = drawSchedulePage(
                document, pageNumber, currentWeekSchedules,
                "$currentTitle - $currentWeekInfoStr (השבוע)",
                "אין שיבוץ לשבוע הנוכחי",
                dayHeaders, colWidths, totalWidth, rowHeight, headerHeight, titleHeight, startX
            )

            // Page 2: Next week
            drawSchedulePage(
                document, pageNumber, nextWeekSchedules,
                "$currentTitle - $nextWeekInfoStr (שבוע הבא)",
                "אין שיבוץ לשבוע הבא",
                dayHeaders, colWidths, totalWidth, rowHeight, headerHeight, titleHeight, startX
            )

            val fileName = "schedule_${currentWeekInfoStr.replace(" ", "_").replace(",", "")}.pdf"
            val savedUri = savePdfToDownloads(document, fileName)

            document.close()

            Toast.makeText(requireContext(), getString(R.string.pdf_saved), Toast.LENGTH_SHORT).show()
            showPdfNotification(fileName, savedUri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.pdf_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawSchedulePage(
        document: PdfDocument,
        pageNumber: Int,
        schedules: List<NurseSchedule>,
        pageTitle: String,
        emptyMessage: String,
        dayHeaders: List<String>,
        colWidths: List<Float>,
        totalWidth: Float,
        rowHeight: Float,
        headerHeight: Float,
        titleHeight: Float,
        startX: Float
    ): Int {
        val dataRows = schedules.size.coerceAtLeast(1)
        val totalHeight = titleHeight + headerHeight + (dataRows * rowHeight) + 60f

        val pageInfo = PdfDocument.PageInfo.Builder(
            totalWidth.toInt(), totalHeight.toInt(), pageNumber
        ).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        val headerPaint = Paint().apply {
            color = Color.WHITE; textSize = 12f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }
        val cellPaint = Paint().apply {
            color = Color.BLACK; textSize = 11f; textAlign = Paint.Align.CENTER
        }
        val headerBgPaint = Paint().apply { color = Color.rgb(25, 50, 100) }
        val rowBgPaint = Paint().apply { color = Color.rgb(240, 240, 245) }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val emptyPaint = Paint().apply {
            color = Color.GRAY; textSize = 14f; textAlign = Paint.Align.CENTER
        }

        // Title
        canvas.drawText(pageTitle, totalWidth - 20f, 30f, titlePaint)

        if (schedules.isEmpty()) {
            canvas.drawText(emptyMessage, totalWidth / 2, titleHeight + 40f, emptyPaint)
            document.finishPage(page)
            return pageNumber + 1
        }

        // Header row
        val headerY = titleHeight
        canvas.drawRect(startX, headerY, totalWidth - 20f, headerY + headerHeight, headerBgPaint)

        var x = startX
        for (i in dayHeaders.indices) {
            canvas.drawText(dayHeaders[i], x + colWidths[i] / 2, headerY + 28f, headerPaint)
            x += colWidths[i]
        }

        // Data rows
        for (row in schedules.indices) {
            val schedule = schedules[row]
            val rowY = titleHeight + headerHeight + (row * rowHeight)

            if (row % 2 == 0) {
                canvas.drawRect(startX, rowY, totalWidth - 20f, rowY + rowHeight, rowBgPaint)
            }
            canvas.drawLine(startX, rowY + rowHeight, totalWidth - 20f, rowY + rowHeight, linePaint)

            val values = listOf(
                schedule.nurseName, schedule.sunday, schedule.monday,
                schedule.tuesday, schedule.wednesday, schedule.thursday,
                schedule.friday, schedule.saturday
            )

            x = startX
            for (i in values.indices) {
                val paint = if (i == 0) Paint(cellPaint).apply { isFakeBoldText = true } else cellPaint
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
            val uri = requireContext().contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw Exception("Failed to create file")
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
                ?: throw Exception("Failed to open output stream")
            outputStream.use { document.writeTo(it) }
            return uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            file.outputStream().use { document.writeTo(it) }
            return Uri.fromFile(file)
        }
    }

    private fun showPdfNotification(fileName: String, fileUri: Uri) {
        val channelId = "pdf_download"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PDF Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = requireContext().getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val pendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("PDF נשמר")
            .setContentText(fileName)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(requireContext()).notify(1001, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.tvEmptyState.visibility = View.VISIBLE
    }

    private fun hideEmptyState() {
        binding.tvEmptyState.visibility = View.GONE
    }

    private fun showRunningState() {
        binding.tvRunningState.visibility = View.VISIBLE
    }

    private fun hideRunningState() {
        binding.tvRunningState.visibility = View.GONE
    }
}
