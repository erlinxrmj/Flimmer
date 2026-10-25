package app.flimmer.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Suche mit Vorschlägen beim Tippen und gespeichertem Suchverlauf. */
public class SearchScreen extends Screen {

    private final VideoGrid grid;
    private final EditText input;
    private final LinearLayout chipRow;
    private final TextView chipTitle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int token;
    private int suggestToken;
    private Repo.Paged paged;
    private String lastQuery;

    public SearchScreen(Activity a) {
        super(a);
        grid = new VideoGrid(a);
        addView(grid, new LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 4));

        // Suchfeld
        LinearLayout field = new LinearLayout(a);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(Ui.dp(a, 18), 0, Ui.dp(a, 18), 0);
        field.setBackground(Ui.round(Ui.SURFACE, Ui.dp(a, 28)));
        ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(Ui.TEXT2);
        field.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 26), Ui.dp(a, 26)));
        input = new EditText(a);
        input.setHint("Suchen");
        input.setHintTextColor(Ui.TEXT2);
        input.setTextColor(Color.WHITE);
        input.setTextSize(20);
        input.setTypeface(Ui.medium(a));
        input.setSingleLine(true);
        input.setBackground(null);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(Ui.dp(a, 14), Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14));
        field.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        input.setOnFocusChangeListener((v, has) -> field.setBackground(has
                ? Ui.round(Ui.SURFACE_HI, Ui.dp(a, 28))
                : Ui.round(Ui.SURFACE, Ui.dp(a, 28))));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                search(input.getText().toString());
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a1) {}

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(suggest);
                handler.postDelayed(suggest, 250);
            }
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.rightMargin = Ui.dp(a, 8);
        header.addView(field, flp);

        // Vorschläge / Suchverlauf
        chipTitle = Ui.text(a, 14, Ui.TEXT2, Ui.medium(a));
        chipTitle.setPadding(Ui.dp(a, 4), Ui.dp(a, 18), 0, Ui.dp(a, 10));
        header.addView(chipTitle);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 6));
        chipRow = new LinearLayout(a);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chipRow);
        header.addView(hs);

        grid.setHeader(header);
        grid.setListener(new VideoGrid.Listener() {
            @Override
            public void onVideo(Vid v) {
                PlayerActivity.open(activity, v.url);
            }

            @Override
            public void onVideoMenu(Vid v) {
                Menus.video(activity, v, null);
            }
        });
    }

    private final Runnable suggest = this::updateChips;

    @Override
    protected void onShow(boolean firstTime) {
        updateChips();
        if (firstTime) grid.showMessage("Suche nach Videos, Kanälen und Themen.\nMit der Mikrofon-Taste der Tastatur geht es auch per Sprache.");
    }

    @Override
    public boolean focusContent() {
        return input.requestFocus();
    }

    @Override
    public void onResumeScreen() {
        updateChips();
    }

    private void updateChips() {
        final String q = input.getText().toString().trim();
        final int t = ++suggestToken;
        if (q.isEmpty()) {
            List<String> recent = App.db().searches("", 15);
            chipTitle.setText(recent.isEmpty() ? "" : "Zuletzt gesucht  •  lange drücken zum Entfernen");
            chipTitle.setVisibility(recent.isEmpty() ? GONE : VISIBLE);
            fillChips(recent, true);
            return;
        }
        Bg.run(() -> Repo.suggestions(q), new Bg.Done<List<String>>() {
            @Override
            public void ok(List<String> remote) {
                if (t != suggestToken || !alive()) return;
                LinkedHashSet<String> all = new LinkedHashSet<>(App.db().searches(q, 4));
                all.addAll(remote);
                chipTitle.setText("Vorschläge");
                chipTitle.setVisibility(VISIBLE);
                fillChips(new ArrayList<>(all), false);
            }

            @Override
            public void fail(Exception e) {
                if (t != suggestToken || !alive()) return;
                fillChips(App.db().searches(q, 10), true);
            }
        });
    }

    private void fillChips(List<String> items, boolean removable) {
        chipRow.removeAllViews();
        Context c = getContext();
        int n = 0;
        for (String s : items) {
            if (++n > 15) break;
            TextView chip = Ui.chip(c, s);
            chip.setOnClickListener(v -> {
                input.setText(s);
                input.setSelection(s.length());
                search(s);
            });
            if (removable) {
                chip.setOnLongClickListener(v -> {
                    App.db().removeSearch(s);
                    updateChips();
                    return true;
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(c, 12);
            chipRow.addView(chip, lp);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    public void search(String q) {
        q = q == null ? "" : q.trim();
        if (q.isEmpty()) return;
        hideKeyboard();
        App.db().addSearch(q);
        lastQuery = q;
        final int t = ++token;
        grid.setOnLoadMore(null);
        grid.showLoading();
        final String query = q;
        Bg.run(() -> Repo.search(query), new Bg.Done<Repo.Paged>() {
            @Override
            public void ok(Repo.Paged p) {
                if (t != token || !alive()) return;
                paged = p;
                if (p.items.isEmpty()) {
                    grid.showMessage("Keine Ergebnisse für „" + query + "“.");
                    return;
                }
                grid.setItems(p.items);
                grid.setOnLoadMore(SearchScreen.this::loadMore);
                grid.focusFirstVideo();
            }

            @Override
            public void fail(Exception e) {
                if (t != token || !alive()) return;
                grid.showMessage("Die Suche hat nicht geklappt.\n" + HomeScreen.msg(e));
            }
        });
    }

    private void loadMore() {
        final Repo.Paged p = paged;
        final int t = token;
        if (p == null || !p.hasMore()) return;
        Bg.run(() -> p.more(true), new Bg.Done<List<Vid>>() {
            @Override
            public void ok(List<Vid> more) {
                if (t != token || !alive()) return;
                if (!more.isEmpty()) grid.append(more);
                else grid.loadMoreDone();
            }

            @Override
            public void fail(Exception e) {
                grid.loadMoreDone();
            }
        });
    }

    @SuppressWarnings("unused")
    private void confirmClear() {
        new AlertDialog.Builder(activity)
                .setTitle("Suchverlauf löschen?")
                .setPositiveButton("Löschen", (d, w) -> {
                    App.db().clearSearches();
                    updateChips();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }
}
