package se.plopmap;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

final class ObjectFields {
    private final LinearLayout container;
    private final SharedPreferences settings;
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private final Map<String, Boolean> required = new LinkedHashMap<>();
    private int mode;

    ObjectFields(LinearLayout container, SharedPreferences settings) {
        this.container = container;
        this.settings = settings;
    }

    void show(int mode, JSONObject values) {
        this.mode = mode;
        container.removeAllViews();
        inputs.clear();
        required.clear();
        for (String line : settings.getString("template_" + mode, "").split("\n")) {
            String label = line.trim();
            boolean mandatory = label.endsWith("*");
            if (mandatory) label = label.substring(0, label.length() - 1).trim();
            if (label.isEmpty() || inputs.containsKey(label)) continue;
            EditText input = new EditText(container.getContext());
            input.setSingleLine(true);
            input.setHint(label + (mandatory ? " (obligatoriskt)" : ""));
            input.setText(values.optString(label, ""));
            container.addView(input);
            inputs.put(label, input);
            required.put(label, mandatory);
        }
    }

    void edit() {
        EditText editor = new EditText(container.getContext());
        editor.setMinLines(3);
        editor.setText(settings.getString("template_" + mode, ""));
        int selected = mode;
        new AlertDialog.Builder(container.getContext()).setTitle("Objektmall")
                .setMessage("Skriv ett fältnamn per rad. Avsluta med * för obligatoriska fält.")
                .setView(editor).setNegativeButton("Avbryt", null)
                .setPositiveButton("Spara mall", (dialog, which) -> {
                    JSONObject draft = values();
                    if (settings.edit().putString("template_" + selected, editor.getText().toString()).commit()) {
                        show(selected, draft);
                    }
                }).show();
    }

    boolean valid() {
        for (var field : inputs.entrySet()) {
            if (required.get(field.getKey()) && field.getValue().getText().toString().trim().isEmpty()) {
                field.getValue().setError("Fyll i " + field.getKey());
                return false;
            }
        }
        return true;
    }

    JSONObject values() {
        JSONObject values = new JSONObject();
        try {
            for (var field : inputs.entrySet()) {
                String value = field.getValue().getText().toString().trim();
                if (!value.isEmpty()) values.put(field.getKey(), value);
            }
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        return values;
    }
}
