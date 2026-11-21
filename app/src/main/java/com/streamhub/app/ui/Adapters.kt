package com.streamhub.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.streamhub.app.core.Episode
import com.streamhub.app.core.SearchResult
import com.streamhub.app.core.Store
import com.streamhub.app.net.Http
import kotlinx.coroutines.CoroutineScope

/** Poster kartlari (yatay satirlar ve favori gridi icin ortak kart gorunumu). */
class CardAdapter(
    private val items: List<SearchResult>,
    private val onClick: (SearchResult) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
    private val cardWidthDp: Int = 110,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = Ui.card(ctx).apply { layoutParams = ViewGroup.MarginLayoutParams(
            Ui.dp(ctx, cardWidthDp), ViewGroup.LayoutParams.WRAP_CONTENT) }
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, (cardWidthDp * 1.45).toInt()))
            layoutParams = lp
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 12f
            maxLines = 2
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 10))
        }
        inner.addView(img)
        inner.addView(title)
        card.addView(inner)
        return object : RecyclerView.ViewHolder(card) {}
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = holder.itemView as ViewGroup
        val ctx = card.context
        val item = items[position]
        val img = card.getChildAt(0) as ViewGroup
        val imgView = img.getChildAt(0) as ImageView
        val title = img.getChildAt(1) as TextView
        title.text = item.title
        Ui.loadPoster(scope, http, imgView, item.posterUrl, card.width, img.layoutParams.height)
        card.setOnClickListener { onClick(item) }
    }
}

/** Arama sonucu satirlari. */
class SearchAdapter(
    private val items: List<SearchResult>,
    private val onClick: (SearchResult) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = Ui.card(ctx)
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 8), Ui.dp(ctx, 10), Ui.dp(ctx, 8))
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(Ui.dp(ctx, 76), Ui.dp(ctx, 108))
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 15f
            maxLines = 2
        }
        row.addView(img)
        row.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = Ui.dp(ctx, 12)
        })
        card.addView(row)
        return object : RecyclerView.ViewHolder(card) {}
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = holder.itemView as ViewGroup
        val row = card.getChildAt(0) as LinearLayout
        val ctx = row.context
        val item = items[position]
        val img = row.getChildAt(0) as ImageView
        val title = row.getChildAt(1) as TextView
        title.text = item.title
        Ui.loadPoster(scope, http, img, item.posterUrl, Ui.dp(ctx, 76), Ui.dp(ctx, 108))
        card.setOnClickListener { onClick(item) }
    }
}

/** Bolum listesi satirlari. */
class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = Ui.card(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12))
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 14f
        }
        row.addView(title)
        card.addView(row)
        card.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return object : RecyclerView.ViewHolder(card) {}
    }

    override fun getItemCount() = episodes.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = holder.itemView as ViewGroup
        val row = card.getChildAt(0) as LinearLayout
        val ctx = row.context
        val ep = episodes[position]
        val tv = row.getChildAt(0) as TextView
        tv.text = buildString {
            if (ep.number != null) append("Bölüm ${ep.number}")
            if (ep.season != null && ep.number != null) clear().append("S${ep.season} · Bölüm ${ep.number}")
            ep.title?.blankToNullSafe()?.let { if (isNotEmpty()) append(" — "); append(it) }
            if (isEmpty()) append(ep.url.substringAfterLast('/'))
        }
        tv.setTextColor(Ui.TEXT_MAIN)
        tv.textSize = 14f
        (card.layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(
            Ui.dp(ctx, 4), Ui.dp(ctx, 3), Ui.dp(ctx, 4), Ui.dp(ctx, 3))
        card.setOnClickListener { onClick(position) }
    }

    private fun String?.blankToNullSafe(): String? = this?.takeIf { it.isNotBlank() }
}

/** Favori grid kartlari. */
class FavAdapter(
    private val items: List<Store.FavItem>,
    private val onClick: (Store.FavItem) -> Unit,
    private val onLongClick: (Store.FavItem) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = Ui.card(ctx)
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 150))
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 12f
            maxLines = 2
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), Ui.dp(ctx, 10))
        }
        inner.addView(img)
        inner.addView(title)
        card.addView(inner)
        return object : RecyclerView.ViewHolder(card) {}
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = holder.itemView as ViewGroup
        val ctx = card.context
        val item = items[position]
        val inner = card.getChildAt(0) as ViewGroup
        val img = inner.getChildAt(0) as ImageView
        val title = inner.getChildAt(1) as TextView
        title.text = item.title
        Ui.loadPoster(scope, http, img, item.posterUrl, card.width, Ui.dp(ctx, 150))
        card.setOnClickListener { onClick(item) }
        card.setOnLongClickListener { onLongClick(item); true }
    }
}

/** "Izlemeye devam et" yatay kartlari. */
class HistoryAdapter(
    private val items: List<Store.HistoryItem>,
    private val onClick: (Store.HistoryItem) -> Unit,
    private val onLongClick: (Store.HistoryItem) -> Unit,
    private val scope: CoroutineScope,
    private val http: Http,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        val card = Ui.card(ctx).apply {
            layoutParams = ViewGroup.MarginLayoutParams(Ui.dp(ctx, 150), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val inner = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 88))
        }
        val title = TextView(ctx).apply {
            setTextColor(Ui.TEXT_MAIN)
            textSize = 12f
            maxLines = 2
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 6), Ui.dp(ctx, 8), 0)
        }
        val pos = TextView(ctx).apply {
            setTextColor(Ui.TEXT_DIM)
            textSize = 11f
            setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 2), Ui.dp(ctx, 8), Ui.dp(ctx, 10))
            setTypeface(typeface, Typeface.BOLD)
        }
        inner.addView(img)
        inner.addView(title)
        inner.addView(pos)
        card.addView(inner)
        return object : RecyclerView.ViewHolder(card) {}
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = holder.itemView as ViewGroup
        val item = items[position]
        val inner = card.getChildAt(0) as ViewGroup
        val img = inner.getChildAt(0) as ImageView
        val title = inner.getChildAt(1) as TextView
        val pos = inner.getChildAt(2) as TextView
        title.text = item.title
        val dur = if (item.durationMs > 0) " / ${Ui.fmtTime(item.durationMs)}" else ""
        pos.text = "${Ui.fmtTime(item.positionMs)}$dur"
        Ui.loadPoster(scope, http, img, item.posterUrl, card.width, Ui.dp(card.context, 88))
        card.setOnClickListener { onClick(item) }
        card.setOnLongClickListener { onLongClick(item); true }
    }
}
