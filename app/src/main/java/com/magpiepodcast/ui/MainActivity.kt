package com.magpiepodcast.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import com.magpiepodcast.MagpieApp
import com.magpiepodcast.R
import com.magpiepodcast.data.Episode
import com.magpiepodcast.data.EpisodeQuery
import com.magpiepodcast.data.EpisodeSort
import com.magpiepodcast.data.ListenStatus
import com.magpiepodcast.data.MagpieApi
import com.magpiepodcast.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val api = MagpieApi(tokenProvider = { MagpieApp.instance.tokenStore.token })

    private var episodes: List<Episode> = emptyList()
    private var showingHistory = false

    private val selectedPrograms = linkedSetOf<String>()
    private val selectedGuests = linkedSetOf<String>()
    private var listenStatus = ListenStatus.ALL
    private var currentSort = EpisodeSort.DATE_NEWEST

    private val sortOptions = listOf(
        EpisodeSort.DATE_NEWEST to "Data (mais recentes)",
        EpisodeSort.DATE_OLDEST to "Data (mais antigos)",
        EpisodeSort.TITLE_AZ to "Título A–Z",
        EpisodeSort.TITLE_ZA to "Título Z–A",
        EpisodeSort.PROGRESS_HIGH to "% assistida (maior)",
        EpisodeSort.PROGRESS_LOW to "% assistida (menor)",
        EpisodeSort.DURATION_LONG to "Duração (mais longos)",
        EpisodeSort.DURATION_SHORT to "Duração (mais curtos)",
        EpisodeSort.RECENTLY_PLAYED to "Ouvidos recentemente"
    )

    private val episodeAdapter = EpisodeListAdapter(
        progressLookup = { MagpieApp.instance.progressRepository.get(it) },
        onClick = { openEpisode(it) }
    )

    private val historyAdapter = HistoryAdapter { progress ->
        EpisodeDetailActivity.start(
            this,
            episodeId = progress.episodeId,
            title = progress.title,
            description = "",
            date = "",
            duration = progress.durationLabel,
            audioUrl = progress.audioUrl,
            image = progress.image,
            resumeMs = progress.positionMs
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!MagpieApp.instance.tokenStore.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = episodeAdapter

        setupStatusChips()
        setupSortSpinner()

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.episodes))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.history))
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showingHistory = tab.position == 1
                binding.filtersPanel.visibility = if (showingHistory) View.GONE else View.VISIBLE
                refreshListUi()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        binding.swipeRefresh.setColorSchemeResources(R.color.magpie_accent)
        binding.swipeRefresh.setOnRefreshListener {
            if (showingHistory) {
                refreshListUi()
                binding.swipeRefresh.isRefreshing = false
            } else {
                loadEpisodes()
            }
        }

        loadEpisodes()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) refreshListUi()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_filters -> {
                clearFilters()
                return true
            }
            R.id.action_logout -> {
                MagpieApp.instance.tokenStore.clear()
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.WebStorage.getInstance().deleteAllData()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupStatusChips() {
        val statuses = listOf(
            ListenStatus.ALL to R.string.status_all,
            ListenStatus.UNPLAYED to R.string.status_unplayed,
            ListenStatus.IN_PROGRESS to R.string.status_progress,
            ListenStatus.COMPLETED to R.string.status_done
        )
        binding.statusChips.removeAllViews()
        statuses.forEach { (status, labelRes) ->
            val chip = Chip(this).apply {
                text = getString(labelRes)
                isCheckable = true
                isChecked = status == ListenStatus.ALL
                setOnClickListener {
                    listenStatus = status
                    for (i in 0 until binding.statusChips.childCount) {
                        val c = binding.statusChips.getChildAt(i) as Chip
                        c.isChecked = c === this
                    }
                    refreshListUi()
                }
            }
            binding.statusChips.addView(chip)
        }
    }

    private fun setupSortSpinner() {
        val labels = sortOptions.map { it.second }
        binding.sortSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels
        )
        binding.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                currentSort = sortOptions[position].first
                refreshListUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private var suppressingChipCallbacks = false

    private fun rebuildFilterChips() {
        suppressingChipCallbacks = true
        try {
            binding.programChips.removeAllViews()
            EpisodeQuery.programTags(episodes).forEach { tag ->
                binding.programChips.addView(
                    Chip(this).apply {
                        text = tag
                        isCheckable = true
                        isChecked = tag in selectedPrograms
                        setOnCheckedChangeListener { _, checked ->
                            if (suppressingChipCallbacks) return@setOnCheckedChangeListener
                            if (checked) selectedPrograms += tag else selectedPrograms -= tag
                            rebuildGuestChips()
                            refreshListUi()
                        }
                    }
                )
            }
            rebuildGuestChips()
        } finally {
            suppressingChipCallbacks = false
        }
    }

    private fun rebuildGuestChips() {
        suppressingChipCallbacks = true
        try {
            binding.guestChips.removeAllViews()
            val guests = EpisodeQuery.guestTags(episodes, selectedPrograms)
            binding.guestLabel.visibility = if (guests.isEmpty()) View.GONE else View.VISIBLE
            selectedGuests.retainAll(guests.toSet())
            guests.forEach { tag ->
                binding.guestChips.addView(
                    Chip(this).apply {
                        text = tag
                        isCheckable = true
                        isChecked = tag in selectedGuests
                        setOnCheckedChangeListener { _, checked ->
                            if (suppressingChipCallbacks) return@setOnCheckedChangeListener
                            if (checked) selectedGuests += tag else selectedGuests -= tag
                            refreshListUi()
                        }
                    }
                )
            }
        } finally {
            suppressingChipCallbacks = false
        }
    }

    private fun clearFilters() {
        selectedPrograms.clear()
        selectedGuests.clear()
        listenStatus = ListenStatus.ALL
        currentSort = EpisodeSort.DATE_NEWEST
        binding.sortSpinner.setSelection(0)
        for (i in 0 until binding.statusChips.childCount) {
            val c = binding.statusChips.getChildAt(i) as Chip
            c.isChecked = i == 0
        }
        rebuildFilterChips()
        refreshListUi()
    }

    private fun loadEpisodes() {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) { api.fetchEpisodes() }
                episodes = response.episodes
                rebuildFilterChips()
                refreshListUi()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_load) + "\n${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.loading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun refreshListUi() {
        if (showingHistory) {
            binding.recycler.adapter = historyAdapter
            val history = MagpieApp.instance.progressRepository.allSorted()
                .filter { it.positionMs > 0 }
            historyAdapter.submitList(history)
            binding.emptyView.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
            binding.emptyView.setText(R.string.empty_history)
            binding.resultCount.text = getString(R.string.result_count, history.size)
        } else {
            binding.recycler.adapter = episodeAdapter
            val filtered = EpisodeQuery.filterAndSort(
                episodes = episodes,
                selectedPrograms = selectedPrograms,
                selectedGuests = selectedGuests,
                status = listenStatus,
                sort = currentSort,
                progressOf = { MagpieApp.instance.progressRepository.get(it) }
            )
            episodeAdapter.submitList(filtered)
            binding.resultCount.text = getString(R.string.result_count, filtered.size)
            val empty = filtered.isEmpty() && episodes.isNotEmpty()
            binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
            if (empty) binding.emptyView.setText(R.string.empty_filtered)
        }
    }

    private fun openEpisode(episode: Episode) {
        val saved = MagpieApp.instance.progressRepository.get(episode.id)
        EpisodeDetailActivity.start(
            this,
            episodeId = episode.id,
            title = episode.title,
            description = episode.description,
            date = episode.date,
            duration = episode.duration,
            audioUrl = episode.audioUrl,
            image = episode.image,
            resumeMs = saved?.positionMs?.takeIf { !saved.isFinished } ?: 0L
        )
    }
}
