package com.streamhub.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.streamhub.app.core.Episode
import com.streamhub.app.core.SearchResult
import com.streamhub.app.net.Http
import kotlinx.coroutines.CoroutineScope

class SectionViewHolder(root: LinearLayout) : RecyclerView.ViewHolder(root) {
    val title: TextView = root.getChildAt(0) as TextView
    val inner: RecyclerView = root.getChildAt(1) as RecyclerView
    val errorText: TextView = root.getChildAt(2) as TextView
}

class HomeAdapter(
    private val rows: List<SectionRow>,
    private val onCardClick: (SearchResult) -> Unit,
    private val onRetry: (Int) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val container = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val title = Ui.sectionTitle(ctx, "")
        val inner = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        }
        val errorText = TextView(ctx).apply {
            setTextColor(Ui.ERROR_RED)
            textSize = 13f
            visibility = View.GONE
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 4), Ui.dp(ctx, 16), Ui.dp(ctx, 8))
        }
        container.addView(title)
        container.addView(inner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(errorText)
        return SectionViewHolder(container)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val h = holder as SectionViewHolder
        val row = rows[position]
        h.title.text = row.title
        if (row.error != null) {
            h.inner.visibility = View.GONE
            h.errorText.visibility = View.VISIBLE
            h.errorText.text = "✗ ${row.error} — yeniden dene"
            h.errorText.setOnClickListener { onRetry(position) }
        } else {
            h.inner.visibility = View.VISIBLE
            h.errorText.visibility = View.GONE
            h.inner.adapter = CardAdapter(row.items, onCardClick, scope, http)
        }
    }
}

class CardViewHolder(val root: LinearLayout) : RecyclerView.ViewHolder(root) {
    val image: ImageView = root.getChildAt(0) as ImageView
    val title: TextView = root.getChildAt(1) as TextView
}

class CardAdapter(
    private val items: List<SearchResult>,
    private val onClick: (SearchResult) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6), Ui.dp(ctx, 4))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 12f
            maxLines = 2
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(ctx, 4), 0, 0)
        }
        card.addView(img)
        card.addView(title)
        card.setOnClickListener { /* bind'de set edilir */ }
        return CardViewHolder(card)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val h = holder as CardViewHolder
        val item = items[position]
        val w = Ui.dp(h.root.context, 110)
        val imgLp = ViewGroup.LayoutParams(w, Ui.dp(h.root.context, 160))
        h.image.layoutParams = imgLp
        h.title.text = item.title
        Ui.loadPoster(scope, http, h.image, item.posterUrl, w, imgLp.height)
        h.root.setOnClickListener { onClick(item) }
    }
}

class SearchAdapter(
    private val items: List<SearchResult>,
    private val onClick: (SearchResult) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        }
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 15f
            maxLines = 2
        }
        row.addView(img)
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(ctx, 12)
        })
        return object : RecyclerView.ViewHolder(row) {
            val image: ImageView = img
            val titleText: TextView = title
        }
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = holder.itemView as LinearLayout
        val ctx = row.context
        val item = items[position]
        val img = (holder.itemView as LinearLayout).getChildAt(0) as ImageView
        val title = (holder.itemView as LinearLayout).getChildAt(1) as TextView
        val w = Ui.dp(ctx, 78)
        img.layoutParams = ViewGroup.LayoutParams(w, Ui.dp(ctx, 112))
        title.text = item.title
        Ui.loadPoster(scope, http, img, item.posterUrl, w, Ui.dp(ctx, 112))
        row.setOnClickListener { onClick(item) }
    }
}

class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(ctx, 16), Ui.dp(ctx, 12), Ui.dp(ctx, 16), Ui.dp(ctx, 12))
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 15f
        }
        row.addView(title)
        return object : RecyclerView.ViewHolder(row) {}
    }

    override fun getItemCount() = episodes.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = holder.itemView as LinearLayout
        val ctx = row.context
        val ep = episodes[position]
        (row.getChildAt(0) as TextView).apply {
            text = buildString {
                if (ep.season != null && ep.number != null) append("S${ep.season}xE${ep.number}")
                else if (ep.number != null) append("Bölüm ${ep.number}")
                ep.title?.takeIf { it.isNotBlank() }?.let { if (isNotEmpty()) append(" · "); append(it) }
                if (isEmpty()) append(ep.url.substringAfterLast('/'))
            }
            setTextColor(Ui.TEXT_MAIN)
            textSize = 15f
        }
        row.setOnClickListener { onClick(position) }
        row.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x14000000)
            cornerRadius = Ui.dp(ctx, 8).toFloat()
        }
        (row.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
            Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 2))
        row.requestLayout()
    }
}
