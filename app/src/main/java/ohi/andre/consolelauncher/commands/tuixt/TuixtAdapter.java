package ohi.andre.consolelauncher.commands.tuixt;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement;
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave;

public class TuixtAdapter extends RecyclerView.Adapter<TuixtAdapter.ViewHolder> {

    private List<XMLPrefsSave> items;
    private final File file;
    private final Map<XMLPrefsSave, String> pendingChanges = new HashMap<>();

    public TuixtAdapter(List<XMLPrefsSave> items, File file) {
        this.items = new ArrayList<>(items);
        this.file = file;
    }

    public void updateList(List<XMLPrefsSave> newList) {
        this.items = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void saveAll() {
        for (Map.Entry<XMLPrefsSave, String> entry : pendingChanges.entrySet()) {
            XMLPrefsSave item = entry.getKey();
            String value = entry.getValue();
            XMLPrefsElement parent = item.parent();
            if (parent != null) {
                parent.write(item, value);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.tuixt_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        XMLPrefsSave item = items.get(position);
        holder.title.setText(item.label());
        holder.description.setText(item.info());

        String currentValue = pendingChanges.containsKey(item) ? pendingChanges.get(item) : XMLPrefsManager.get(item);

        holder.input.removeTextChangedListener(holder.textWatcher);
        holder.toggle.setOnCheckedChangeListener(null);
        holder.colorPreview.setOnClickListener(null);

        if (XMLPrefsSave.BOOLEAN.equals(item.type())) {
            holder.toggle.setVisibility(View.VISIBLE);
            holder.colorPreview.setVisibility(View.GONE);
            holder.input.setVisibility(View.GONE);
            holder.toggle.setChecked(Boolean.parseBoolean(currentValue));
            holder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                pendingChanges.put(item, String.valueOf(isChecked));
            });
        } else if (XMLPrefsSave.COLOR.equals(item.type())) {
            holder.toggle.setVisibility(View.GONE);
            holder.colorPreview.setVisibility(View.VISIBLE);
            holder.input.setVisibility(View.VISIBLE);
            holder.input.setText(currentValue);
            updateColorPreview(holder.colorPreview, currentValue);

            holder.colorPreview.setOnClickListener(v -> showColorPicker(holder, item, holder.input.getText().toString()));

            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    String val = s.toString();
                    if (val.matches("^#[0-9A-Fa-f]{6,8}$")) {
                        updateColorPreview(holder.colorPreview, val);
                        pendingChanges.put(item, val);
                    }
                }
            };
            holder.input.addTextChangedListener(holder.textWatcher);
        } else {
            holder.toggle.setVisibility(View.GONE);
            holder.colorPreview.setVisibility(View.GONE);
            holder.input.setVisibility(View.VISIBLE);
            holder.input.setText(currentValue);
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    pendingChanges.put(item, s.toString());
                }
            };
            holder.input.addTextChangedListener(holder.textWatcher);
        }
    }

    private void updateColorPreview(View view, String hex) {
        try {
            view.setBackgroundColor(Color.parseColor(hex));
        } catch (Exception e) {
            view.setBackgroundColor(Color.BLACK);
        }
    }

    private void showColorPicker(ViewHolder holder, XMLPrefsSave item, String currentHex) {
        View dialogView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.color_picker_dialog, null);
        View preview = dialogView.findViewById(R.id.color_preview);
        SeekBar seekAlpha = dialogView.findViewById(R.id.seek_alpha);
        SeekBar seekHue = dialogView.findViewById(R.id.seek_hue);
        SeekBar seekSat = dialogView.findViewById(R.id.seek_sat);
        SeekBar seekVal = dialogView.findViewById(R.id.seek_val);
        TextView hexText = dialogView.findViewById(R.id.hex_preview);

        int initialColor;
        try {
            initialColor = Color.parseColor(currentHex);
        } catch (Exception e) {
            initialColor = Color.WHITE;
        }

        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);
        int alpha = Color.alpha(initialColor);

        seekAlpha.setProgress(alpha);
        seekHue.setProgress((int) hsv[0]);
        seekSat.setProgress((int) (hsv[1] * 100));
        seekVal.setProgress((int) (hsv[2] * 100));

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float[] newHsv = new float[]{(float) seekHue.getProgress(), (float) seekSat.getProgress() / 100f, (float) seekVal.getProgress() / 100f};
                int newAlpha = seekAlpha.getProgress();
                int newColor = Color.HSVToColor(newAlpha, newHsv);
                preview.setBackgroundColor(newColor);
                String hex = String.format("#%08X", newColor);
                hexText.setText(hex);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        seekAlpha.setOnSeekBarChangeListener(listener);
        seekHue.setOnSeekBarChangeListener(listener);
        seekSat.setOnSeekBarChangeListener(listener);
        seekVal.setOnSeekBarChangeListener(listener);

        // Initial trigger
        listener.onProgressChanged(null, 0, false);

        new AlertDialog.Builder(holder.itemView.getContext(), AlertDialog.THEME_HOLO_DARK)
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String finalHex = hexText.getText().toString();
                    holder.input.setText(finalHex);
                    pendingChanges.put(item, finalHex);
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, description;
        SwitchCompat toggle;
        View colorPreview;
        EditText input;
        TextWatcher textWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.setting_title);
            description = itemView.findViewById(R.id.setting_description);
            toggle = itemView.findViewById(R.id.setting_switch);
            colorPreview = itemView.findViewById(R.id.setting_color_preview);
            input = itemView.findViewById(R.id.setting_input);
        }
    }
}
