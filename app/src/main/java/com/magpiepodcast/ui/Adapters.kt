package com.magpiepodcast.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.magpiepodcast.R
import com.magpiepodcast.data.ApiConfig
import com.magpiepodcast.data.Episode
import com.magpiepodcast.data.EpisodeQuery
import com.magpiepodcast.data.Formatters
import com.magpiepodcast.data.PlaybackProgress
import com.magpiepodcast.databinding.ItemEpisodeBinding

class EpisodeListAdapter(
    private val progressLookup: (Int) -> PlaybackProgress?,
    private val onClick: (Episode) -> Unit
) : ListAdapter<Episode, EpisodeListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(episode: Episode) {
            val ctx = binding.root.context
            binding.title.text = episode.title
            binding.meta.text = buildString {
                append(Formatters.formatDate(episode.date))
                append(" · ")
                append(episode.duration)
            }

            if (episode.tags.isNotEmpty()) {
                binding.tagsLine.visibility = View.VISIBLE
                binding.tagsLine.text = episode.tags.joinToString(" · ")
            } else {
                binding.tagsLine.visibility = View.GONE
            }

            binding.thumbnail.load(ApiConfig.mediaUrl(episode.image)) {
                crossfade(true)
            }

            val progress = progressLookup(episode.id)
            val pct = EpisodeQuery.progressPercent(progress, episode.duration)
            when {
                progress?.isFinished == true || pct >= 100 -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 1000
                    binding.progressBar.progressTintList =
                        ContextCompat.getColorStateList(ctx, R.color.magpie_done)
                    binding.progressBadge.visibility = View.VISIBLE
                    binding.progressBadge.text = ctx.getString(R.string.progress_done)
                    binding.progressBadge.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.magpie_done)
                    )
                }
                pct > 0 -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = (pct * 10).coerceIn(1, 999)
                    binding.progressBar.progressTintList =
                        ContextCompat.getColorStateList(ctx, R.color.magpie_partial)
                    binding.progressBadge.visibility = View.VISIBLE
                    binding.progressBadge.text = ctx.getString(R.string.progress_pct, pct)
                    binding.progressBadge.setBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.magpie_partial)
                    )
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressBadge.visibility = View.GONE
                }
            }

            binding.root.setOnClickListener { onClick(episode) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Episode>() {
            override fun areItemsTheSame(oldItem: Episode, newItem: Episode) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Episode, newItem: Episode) =
                oldItem == newItem
        }
    }
}

class HistoryAdapter(
    private val onClick: (PlaybackProgress) -> Unit
) : ListAdapter<PlaybackProgress, HistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEpisodeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemEpisodeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PlaybackProgress) {
            val ctx = binding.root.context
            binding.title.text = item.title
            binding.meta.text = buildString {
                append("Parou em ")
                append(Formatters.formatMs(item.positionMs))
                if (item.durationMs > 0) {
                    append(" / ")
                    append(Formatters.formatMs(item.durationMs))
                } else if (item.durationLabel.isNotBlank()) {
                    append(" · ")
                    append(item.durationLabel)
                }
            }
            binding.tagsLine.visibility = View.GONE
            binding.thumbnail.load(ApiConfig.mediaUrl(item.image)) {
                crossfade(true)
            }
            val pct = EpisodeQuery.progressPercent(item, item.durationLabel)
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = (pct * 10).coerceIn(0, 1000)
            binding.progressBadge.visibility = View.VISIBLE
            if (item.isFinished || pct >= 100) {
                binding.progressBadge.text = ctx.getString(R.string.progress_done)
                binding.progressBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.magpie_done)
                )
                binding.progressBar.progressTintList =
                    ContextCompat.getColorStateList(ctx, R.color.magpie_done)
            } else {
                binding.progressBadge.text = ctx.getString(R.string.progress_pct, pct)
                binding.progressBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.magpie_partial)
                )
                binding.progressBar.progressTintList =
                    ContextCompat.getColorStateList(ctx, R.color.magpie_partial)
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PlaybackProgress>() {
            override fun areItemsTheSame(oldItem: PlaybackProgress, newItem: PlaybackProgress) =
                oldItem.episodeId == newItem.episodeId

            override fun areContentsTheSame(oldItem: PlaybackProgress, newItem: PlaybackProgress) =
                oldItem == newItem
        }
    }
}
