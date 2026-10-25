package app.flimmer.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Raster aus Video-Kacheln mit Vorschaubild, Dauer, Titel und „Kanal • Aufrufe • Datum“,
 * optional mit einer Kopfzeile (Filter-Chips, Kanal-Banner …) und Abschnitts-Überschriften.
 */
public class VideoGrid extends FrameLayout {

    public interface Listener {
        void onVideo(Vid v);

        default void onVideoMenu(Vid v) {}
    }

    /** Überschrift zwischen Abschnitten, z. B. „Neu von deinen Kanälen“. */
    public static class Section {
        public final String title;
        public final String subtitle;

        public Section(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public static final int COLUMNS = 4;

    private static final int T_HEADER = 0, T_SECTION = 1, T_VIDEO = 2, T_CHANNEL = 3;

    private final RecyclerView list;
    private final ProgressBar progress;
    private final TextView message;
    private final Adapter adapter = new Adapter();
    private final List<Object> items = new ArrayList<>();
    private View header;
    private Listener listener;
    private Runnable loadMore;
    private boolean loadingMore;
    private boolean showUploader = true;

    public VideoGrid(Context c) {
        super(c);
        list = new RecyclerView(c);
        GridLayoutManager glm = new GridLayoutManager(c, COLUMNS);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int t = adapter.getItemViewType(position);
                return (t == T_HEADER || t == T_SECTION) ? COLUMNS : 1;
            }
        });
        list.setLayoutManager(glm);
        list.setAdapter(adapter);
        list.setClipToPadding(false);
        list.setClipChildren(false);
        int side = Ui.dp(c, 28);
        list.setPadding(side, Ui.dp(c, 12), side, Ui.dp(c, 48));
        list.setItemAnimator(null);
        list.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(c);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        LayoutParams plp = new LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48), Gravity.CENTER);
        addView(progress, plp);
        progress.setVisibility(GONE);

        message = Ui.text(c, 17, Ui.TEXT2, Ui.medium(c));
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(Ui.dp(c, 4), 1f);
        int mp = Ui.dp(c, 60);
        message.setPadding(mp, 0, mp, 0);
        LayoutParams mlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        addView(message, mlp);
        message.setVisibility(GONE);
    }

    public RecyclerView list() {
        return list;
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setShowUploader(boolean show) {
        showUploader = show;
    }

    public void setHeader(View h) {
        header = h;
        rebuild(new ArrayList<>(contentItems()));
    }

    public void setOnLoadMore(Runnable r) {
        loadMore = r;
        loadingMore = false;
    }

    public void loadMoreDone() {
        loadingMore = false;
    }

    private List<Object> contentItems() {
        List<Object> out = new ArrayList<>();
        for (Object o : items) if (o != header) out.add(o);
        return out;
    }

    private void rebuild(List<Object> content) {
        items.clear();
        if (header != null) items.add(header);
        items.addAll(content);
        adapter.notifyDataSetChanged();
    }

    /** Inhalte ersetzen (Vid-Objekte und Section-Überschriften). */
    public void setItems(List<?> content) {
        progress.setVisibility(GONE);
        message.setVisibility(GONE);
        rebuild(new ArrayList<>(content));
        list.scrollToPosition(0);
    }

    public void append(List<?> more) {
        int start = items.size();
        items.addAll(more);
        adapter.notifyItemRangeInserted(start, more.size());
        loadingMore = false;
    }

    public int contentCount() {
        return items.size() - (header != null ? 1 : 0);
    }

    public void showLoading() {
        rebuild(new ArrayList<>());
        message.setVisibility(GONE);
        progress.setVisibility(VISIBLE);
        if (header != null) {
            LayoutParams lp = (LayoutParams) progress.getLayoutParams();
            lp.gravity = Gravity.CENTER;
            lp.topMargin = Ui.dp(getContext(), 80);
            progress.setLayoutParams(lp);
        }
    }

    public void showMessage(String text) {
        rebuild(new ArrayList<>());
        progress.setVisibility(GONE);
        message.setText(text);
        message.setVisibility(VISIBLE);
        if (header != null) {
            LayoutParams lp = (LayoutParams) message.getLayoutParams();
            lp.topMargin = Ui.dp(getContext(), 80);
            message.setLayoutParams(lp);
        }
    }

    /** Fokus auf die erste Kachel legen (nicht auf die Kopfzeile). */
    public boolean focusFirstVideo() {
        int first = header != null ? 1 : 0;
        while (first < items.size() && items.get(first) instanceof Section) first++;
        if (first >= items.size()) return header != null && header.requestFocus();
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(first);
        if (vh != null) return vh.itemView.requestFocus();
        final int pos = first;
        list.scrollToPosition(pos);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(pos);
            if (h != null) h.itemView.requestFocus();
        });
        return true;
    }

    // ------------------------------------------------------------------

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemViewType(int position) {
            Object o = items.get(position);
            if (o == header && header != null) return T_HEADER;
            if (o instanceof Section) return T_SECTION;
            if (o instanceof Vid && ((Vid) o).isChannel) return T_CHANNEL;
            return T_VIDEO;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == T_HEADER) {
                FrameLayout holder = new FrameLayout(c);
                holder.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(holder) {};
            }
            if (type == T_SECTION) return new SectionHolder(c);
            if (type == T_CHANNEL) return new ChannelHolder(c);
            return new VideoHolder(c);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Object o = items.get(position);
            if (h instanceof VideoHolder) ((VideoHolder) h).bind((Vid) o);
            else if (h instanceof ChannelHolder) ((ChannelHolder) h).bind((Vid) o);
            else if (h instanceof SectionHolder) ((SectionHolder) h).bind((Section) o);
            else {
                FrameLayout f = (FrameLayout) h.itemView;
                if (f.getChildCount() == 0 || f.getChildAt(0) != header) {
                    f.removeAllViews();
                    if (header.getParent() instanceof ViewGroup) ((ViewGroup) header.getParent()).removeView(header);
                    f.addView(header);
                }
            }
            if (loadMore != null && !loadingMore && position >= items.size() - COLUMNS * 2) {
                loadingMore = true;
                list.post(loadMore);
            }
        }
    }

    private static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView title, subtitle;

        SectionHolder(Context c) {
            super(new LinearLayout(c));
            LinearLayout l = (LinearLayout) itemView;
            l.setOrientation(LinearLayout.VERTICAL);
            l.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            l.setPadding(Ui.dp(c, 8), Ui.dp(c, 22), Ui.dp(c, 8), Ui.dp(c, 10));
            title = Ui.text(c, 22, Ui.TEXT, Ui.bold(c));
            subtitle = Ui.text(c, 14, Ui.TEXT2, Ui.regular(c));
            subtitle.setPadding(0, Ui.dp(c, 6), 0, 0);
            l.addView(title);
            l.addView(subtitle);
        }

        void bind(Section s) {
            title.setText(s.title);
            subtitle.setText(s.subtitle);
            subtitle.setVisibility(s.subtitle == null ? View.GONE : View.VISIBLE);
        }
    }

    /** Vorschaubild im 16:9-Format. */
    static class Thumb extends FrameLayout {
        Thumb(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(width * 9 / 16, MeasureSpec.EXACTLY));
        }
    }

    private class VideoHolder extends RecyclerView.ViewHolder {
        final Thumb thumbBox;
        final ImageView thumb;
        final TextView duration, title, meta;
        final View progressTrack, progressFill;
        final View ring;
        Vid vid;

        VideoHolder(Context c) {
            super(new LinearLayout(c));
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            int m = Ui.dp(c, 8);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(m, m, m, Ui.dp(c, 14));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 10));

            float radius = Ui.dp(c, 12);
            thumbBox = new Thumb(c);
            thumbBox.setBackground(Ui.round(Ui.SURFACE, radius));
            Ui.clipRound(thumbBox, radius);
            thumb = new ImageView(c);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbBox.addView(thumb, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            duration = Ui.text(c, 12, Color.WHITE, Ui.semibold(c));
            duration.setBackground(Ui.round(0xCC000000, Ui.dp(c, 4)));
            duration.setPadding(Ui.dp(c, 5), Ui.dp(c, 3), Ui.dp(c, 5), Ui.dp(c, 3));
            LayoutParams dlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            dlp.setMargins(0, 0, Ui.dp(c, 8), Ui.dp(c, 8));
            thumbBox.addView(duration, dlp);

            progressTrack = new View(c);
            progressTrack.setBackgroundColor(0x99717171);
            thumbBox.addView(progressTrack, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 4), Gravity.BOTTOM));
            progressFill = new View(c);
            progressFill.setBackgroundColor(Ui.ACCENT);
            thumbBox.addView(progressFill, new LayoutParams(0, Ui.dp(c, 4), Gravity.BOTTOM | Gravity.START));

            ring = new View(c);
            ring.setBackground(Ui.ring(Color.WHITE, Ui.dp(c, 3), radius));
            ring.setVisibility(GONE);
            thumbBox.addView(ring, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            card.addView(thumbBox, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            title = Ui.lines(Ui.text(c, 15, Ui.TEXT, Ui.semibold(c)), 2);
            title.setLineSpacing(Ui.dp(c, 3), 1f);
            title.setPadding(Ui.dp(c, 2), Ui.dp(c, 10), Ui.dp(c, 2), 0);
            card.addView(title);
            meta = Ui.lines(Ui.text(c, 12.5f, Ui.TEXT2, Ui.regular(c)), 2);
            meta.setLineSpacing(Ui.dp(c, 2), 1f);
            meta.setPadding(Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2), 0);
            card.addView(meta);

            card.setOnFocusChangeListener((v, has) -> {
                ring.setVisibility(has ? VISIBLE : GONE);
                card.setBackground(has ? Ui.round(0x1FFFFFFF, Ui.dp(c, 16)) : null);
                card.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(140).start();
                card.setElevation(has ? Ui.dp(c, 8) : 0);
            });
            card.setOnClickListener(v -> {
                if (listener != null && vid != null) listener.onVideo(vid);
            });
            card.setOnLongClickListener(v -> {
                if (listener != null && vid != null) listener.onVideoMenu(vid);
                return true;
            });
        }

        void bind(Vid v) {
            vid = v;
            title.setText(v.title);
            meta.setText(Fmt.meta(v, showUploader));
            if (v.live) {
                duration.setText("LIVE");
                duration.setBackground(Ui.round(Ui.LIVE, Ui.dp(itemView.getContext(), 4)));
                duration.setVisibility(VISIBLE);
            } else {
                String d = v.isShort && v.durationSec <= 0 ? "SHORTS" : Fmt.duration(v.durationSec);
                duration.setText(d);
                duration.setBackground(Ui.round(0xCC000000, Ui.dp(itemView.getContext(), 4)));
                duration.setVisibility(d == null ? GONE : VISIBLE);
            }
            boolean showProgress = v.positionMs > 5000 && v.durationSec > 0;
            progressTrack.setVisibility(showProgress ? VISIBLE : GONE);
            progressFill.setVisibility(showProgress ? VISIBLE : GONE);
            if (showProgress) {
                float frac = Math.min(1f, v.positionMs / (v.durationSec * 1000f));
                thumbBox.post(() -> {
                    ViewGroup.LayoutParams lp = progressFill.getLayoutParams();
                    lp.width = Math.max(Ui.dp(itemView.getContext(), 4), (int) (thumbBox.getWidth() * frac));
                    progressFill.setLayoutParams(lp);
                });
            }
            Glide.with(thumb).load(v.thumb).placeholder(new ColorDrawable(Ui.SURFACE)).centerCrop().into(thumb);
        }
    }

    private class ChannelHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name, subs;
        Vid vid;

        ChannelHolder(Context c) {
            super(new LinearLayout(c));
            LinearLayout card = (LinearLayout) itemView;
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            int m = Ui.dp(c, 8);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(m, m, m, Ui.dp(c, 14));
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(m, Ui.dp(c, 12), m, Ui.dp(c, 12));

            avatar = new ImageView(c);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable ph = new GradientDrawable();
            ph.setShape(GradientDrawable.OVAL);
            ph.setColor(Ui.SURFACE);
            avatar.setBackground(ph);
            Ui.clipCircle(avatar);
            card.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(c, 96), Ui.dp(c, 96)));

            name = Ui.lines(Ui.text(c, 15, Ui.TEXT, Ui.semibold(c)), 1);
            name.setPadding(0, Ui.dp(c, 12), 0, 0);
            card.addView(name);
            subs = Ui.lines(Ui.text(c, 12.5f, Ui.TEXT2, Ui.regular(c)), 1);
            subs.setPadding(0, Ui.dp(c, 5), 0, 0);
            card.addView(subs);

            card.setOnFocusChangeListener((v, has) -> {
                card.setBackground(has ? Ui.round(0x26FFFFFF, Ui.dp(c, 16)) : null);
                card.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(140).start();
            });
            card.setOnClickListener(v -> {
                if (vid != null) ChannelActivity.open(c, vid.url, vid.title);
            });
        }

        void bind(Vid v) {
            vid = v;
            name.setText(v.title);
            String s = Fmt.subs(v.subscribers);
            subs.setText(s == null ? "Kanal" : s);
            Glide.with(avatar).load(v.thumb).circleCrop().into(avatar);
        }
    }
}
