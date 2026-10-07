package com.jarvis.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    // 0 = nada, 1 = apertar "enviar", 2 = apertar ligação de voz, 3 = confirmar ligação
    static volatile int mode = 0;
    static volatile long until = 0;

    TextToSpeech tts;
    boolean ttsOk = false, listenAfter = false, viaWa = false;
    TextView log;
    EditText input;
    ScrollView sv;
    String stage = null, pi = null, cName = null, cNum = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 64, 24, 24);
        TextView title = new TextView(this);
        title.setText("J.A.R.V.I.S");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        sv = new ScrollView(this);
        log = new TextView(this);
        log.setTextSize(16);
        sv.addView(log);
        root.addView(title);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout bar = new LinearLayout(this);
        input = new EditText(this);
        input.setHint("Escreva um comando");
        input.setSingleLine(true);
        Button send = new Button(this);
        send.setText("➤");
        Button mic = new Button(this);
        mic.setText("🎤");
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(send);
        bar.addView(mic);
        root.addView(bar);
        setContentView(root);

        send.setOnClickListener(v -> {
            String s = input.getText().toString();
            input.setText("");
            handle(s);
        });
        mic.setOnClickListener(v -> listen());

        tts = new TextToSpeech(this, this);
        requestPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS}, 2);
        add("Jarvis: Olá! Toque no 🎤 e diga um comando. Ex.: \"mande uma mensagem pro meu avô\".");
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("pt", "BR"));
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onError(String id) {}
                @Override public void onDone(String id) {
                    runOnUiThread(() -> {
                        if (listenAfter) {
                            listenAfter = false;
                            listen();
                        }
                    });
                }
            });
            ttsOk = true;
        }
    }

    void add(String s) {
        log.append(s + "\n\n");
        sv.post(() -> sv.fullScroll(ScrollView.FOCUS_DOWN));
    }

    void say(String t, boolean listen) {
        add("Jarvis: " + t);
        if (ttsOk) {
            listenAfter = listen;
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u");
        } else if (listen) {
            listen();
        }
    }

    void listen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        try {
            startActivityForResult(i, 1);
        } catch (Exception e) {
            say("Não consegui abrir o reconhecimento de voz. Digite o comando.", false);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 1 && res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) handle(r.get(0));
        }
    }

    static String norm(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    String who(String t) {
        Matcher m = Pattern.compile("\\b(?:pro|pra|para|ao|a)\\s+(?:o\\s+|a\\s+)?(?:meu\\s+|minha\\s+|seu\\s+)?([a-z ]+?)(?:\\s+(?:pelo|no|via|dizendo|por)\\b.*)?$").matcher(t);
        return m.find() ? m.group(1).trim() : null;
    }

    void handle(String raw) {
        raw = raw.trim();
        if (raw.isEmpty()) return;
        add("Você: " + raw);
        String t = norm(raw).replaceFirst("^\\s*(jarvis|jarves|jarvi)[,.\\s]*", "").trim();

        if (stage != null) {
            String s = stage;
            stage = null;
            if (s.equals("msg")) sendMsg(raw);
            else if (s.equals("who")) go(pi, t.replaceFirst("^(o|a|meu|minha)\\s+", "").trim(), viaWa);
            else if (s.equals("app")) openApp(t);
            return;
        }

        boolean isCall = Pattern.compile("\\b(lig\\w*|chamar|chama|telefon\\w*|ligacao)\\b").matcher(t).find();
        boolean isMsg = Pattern.compile("(mensagem|msg|recado|mand\\w*|envi\\w*|escrev\\w*|avis\\w*)").matcher(t).find();
        boolean hasMsgWord = t.contains("mensagem") || t.contains("msg") || t.contains("recado");
        boolean wa = t.contains("whats") || t.contains("zap");

        if (isCall && !hasMsgWord) {
            String n = who(t);
            if (n != null) go("call", n, wa);
            else { pi = "call"; viaWa = wa; stage = "who"; say("Para quem?", true); }
            return;
        }
        if (isMsg) {
            String n = who(t);
            if (n != null) go("msg", n, false);
            else { pi = "msg"; viaWa = false; stage = "who"; say("Para quem?", true); }
            return;
        }
        if (Pattern.compile("\\b(abr(a|ir|e)|iniciar)\\b").matcher(t).find()) {
            Matcher m = Pattern.compile("\\b(?:abr(?:a|ir|e)|iniciar)\\s+(?:o\\s+|a\\s+)?(?:aplicativo|app)?\\s*(?:do\\s+|de\\s+)?(.*)$").matcher(t);
            String n = m.find() ? m.group(1).trim() : "";
            if (!n.isEmpty()) openApp(n);
            else { stage = "app"; say("Qual será o aplicativo?", true); }
            return;
        }
        String r = calc(t);
        if (r != null) { say("O resultado é " + r + ".", false); return; }
        say("Não entendi. Tente: mande uma mensagem pro meu avô, ligue para a mãe, abra o YouTube, ou quanto é 15% de 200.", false);
    }

    void go(String kind, String name, boolean wa) {
        String num = resolve(name);
        if (num == null) {
            say("Não encontrei " + name + " nos contatos. Salve o contato com esse nome na agenda.", false);
            return;
        }
        cName = name;
        cNum = num;
        if (kind.equals("msg")) {
            stage = "msg";
            say("Certo! Qual será a mensagem para " + name + "?", true);
        } else if (wa) {
            whatsappCall();
        } else {
            phoneCall();
        }
    }

    String resolve(String name) {
        if (name.equals("avo") || name.equals("vo") || name.equals("vovo")) return "5579991537519";
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        String dn = c.getString(0), nb = c.getString(1);
                        if (dn != null && nb != null && norm(dn).contains(name)) {
                            String d = nb.replaceAll("\\D", "");
                            if (d.length() <= 11) d = "55" + d;
                            return d;
                        }
                    }
                } finally {
                    c.close();
                }
            }
        }
        return null;
    }

    boolean accOn() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    void openWa(Uri u) {
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        i.setPackage("com.whatsapp");
        try {
            startActivity(i);
        } catch (Exception e) {
            i.setPackage(null);
            try {
                startActivity(i);
            } catch (Exception e2) {
                mode = 0;
                say("Não consegui abrir o WhatsApp.", false);
            }
        }
    }

    void needAcc() {
        say("Para eu apertar o botão sozinho, ative o Jarvis em Acessibilidade. Vou abrir a tela.", false);
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    void sendMsg(String text) {
        if (accOn()) {
            mode = 1;
            until = System.currentTimeMillis() + 20000;
        } else {
            say("A acessibilidade do Jarvis está desligada, então você precisará tocar em enviar.", false);
        }
        openWa(Uri.parse("https://wa.me/" + cNum + "?text=" + Uri.encode(text)));
        if (accOn()) say("Enviando para " + cName + ".", false);
    }

    void phoneCall() {
        Uri tel = Uri.parse("tel:+" + cNum);
        say("Ligando para " + cName + ".", false);
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(Intent.ACTION_CALL, tel));
        } else {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 2);
            startActivity(new Intent(Intent.ACTION_DIAL, tel));
        }
    }

    void whatsappCall() {
        if (!accOn()) { needAcc(); return; }
        mode = 2;
        until = System.currentTimeMillis() + 15000;
        say("Ligando para " + cName + " pelo WhatsApp.", false);
        openWa(Uri.parse("https://wa.me/" + cNum));
    }

    void openApp(String name) {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            String lbl = norm(ri.loadLabel(pm).toString());
            if (lbl.contains(name) || name.contains(lbl)) {
                Intent li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (li != null) {
                    say("Abrindo " + ri.loadLabel(pm) + ".", false);
                    startActivity(li);
                    return;
                }
            }
        }
        say("Não achei esse aplicativo no celular.", false);
    }

    // ---------- contas ----------
    String s;
    int pos;

    double num(String x) { return Double.parseDouble(x.replace(',', '.')); }

    String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return null;
        if (v == Math.rint(v) && Math.abs(v) < 1e12) return String.valueOf((long) v);
        return new DecimalFormat("#.########", new DecimalFormatSymbols(new Locale("pt", "BR"))).format(v);
    }

    String calc(String t) {
        String e = t.replaceAll("quanto (e|da|faz|fica|sera)\\s*", "").replace("?", "");
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:%|por cento)\\s*de\\s*(\\d+(?:[.,]\\d+)?)").matcher(e);
        if (m.find()) return fmt(num(m.group(1)) / 100 * num(m.group(2)));
        m = Pattern.compile("raiz quadrada de (\\d+(?:[.,]\\d+)?)").matcher(e);
        if (m.find()) return fmt(Math.sqrt(num(m.group(1))));
        e = e.replaceAll("(\\d+(?:[.,]\\d+)?) ao quadrado", "($1*$1)")
                .replaceAll("(\\d+(?:[.,]\\d+)?) ao cubo", "($1*$1*$1)")
                .replace("dividido por", "/").replace("dividido", "/")
                .replace("multiplicado por", "*").replaceAll("\\bvezes\\b", "*").replaceAll("\\bx\\b", "*")
                .replaceAll("\\bmais\\b", "+").replaceAll("\\bmenos\\b", "-")
                .replaceAll("(\\d),(\\d)", "$1.$2").trim();
        if (!e.matches("[0-9+\\-*/().\\s]+") || !e.matches(".*\\d.*")) return null;
        try {
            s = e;
            pos = 0;
            double v = expr();
            sp();
            if (pos != s.length()) return null;
            return fmt(v);
        } catch (Exception x) {
            return null;
        }
    }

    void sp() { while (pos < s.length() && s.charAt(pos) == ' ') pos++; }

    double expr() {
        double v = term();
        for (;;) {
            sp();
            if (pos < s.length() && s.charAt(pos) == '+') { pos++; v += term(); }
            else if (pos < s.length() && s.charAt(pos) == '-') { pos++; v -= term(); }
            else return v;
        }
    }

    double term() {
        double v = fac();
        for (;;) {
            sp();
            if (pos < s.length() && s.charAt(pos) == '*') { pos++; v *= fac(); }
            else if (pos < s.length() && s.charAt(pos) == '/') { pos++; v /= fac(); }
            else return v;
        }
    }

    double fac() {
        sp();
        if (pos < s.length() && s.charAt(pos) == '-') { pos++; return -fac(); }
        if (pos < s.length() && s.charAt(pos) == '(') {
            pos++;
            double v = expr();
            sp();
            if (pos >= s.length() || s.charAt(pos) != ')') throw new RuntimeException();
            pos++;
            return v;
        }
        int st = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
        if (st == pos) throw new RuntimeException();
        return Double.parseDouble(s.substring(st, pos));
    }
}