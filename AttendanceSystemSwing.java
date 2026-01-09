import java.awt.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class AttendanceSystemSwing extends JFrame {

    // -------- Fixed Subjects + Timetable --------
    static final class Subject implements Serializable {
        String code, title, lecturerName, lecturerUsername;
        Subject(String code, String title, String lecturerName, String lecturerUsername) {
            this.code = code; this.title = title; this.lecturerName = lecturerName; this.lecturerUsername = lecturerUsername;
        }
        @Override public String toString() { return code + " - " + title + " (" + lecturerName + ")"; }
    }

    static final List<Subject> SUBJECTS = List.of(
            new Subject("HNDIT 1012", "Visual Application Programming", "Mr. J. R. Jayasinghe", "lect1012"),
            new Subject("HNDIT 1022", "Web Design", "Ms. H. A. P. Anusha", "lect1022"),
            new Subject("HNDIT 1032", "Computer Network Systems", "Ms. S. M. M. Malika", "lect1032"),
            new Subject("HNDIT 1042", "Information Management Systems", "Mr. Kannangara", "lect1042"),
            new Subject("HNDIT 1062", "Communication Skills", "Ms. Renuka", "lect1062")
    );

    static final Map<DayOfWeek, String> TIMETABLE = Map.of(
            DayOfWeek.MONDAY, "HNDIT 1022",
            DayOfWeek.TUESDAY, "HNDIT 1012",
            DayOfWeek.WEDNESDAY, "HNDIT 1032",
            DayOfWeek.THURSDAY, "HNDIT 1042",
            DayOfWeek.FRIDAY, "HNDIT 1062"
    );

    static final String TIME_RANGE = "08:00 to 15:00";

    // -------- Users --------
    enum Role { ADMIN, LECTURER }
    static final class User implements Serializable {
        String username, password;
        Role role;
        User(String u, String p, Role r) { username=u; password=p; role=r; }
    }

    static final Map<String, User> USERS = new HashMap<>();
    static {
        USERS.put("admin",   new User("admin", "admin123", Role.ADMIN));
        USERS.put("lect1012", new User("lect1012", "lect123", Role.LECTURER));
        USERS.put("lect1022", new User("lect1022", "lect123", Role.LECTURER));
        USERS.put("lect1032", new User("lect1032", "lect123", Role.LECTURER));
        USERS.put("lect1042", new User("lect1042", "lect123", Role.LECTURER));
        USERS.put("lect1062", new User("lect1062", "lect123", Role.LECTURER));
    }

    // -------- Data --------
    static final class Student implements Serializable {
        String regNo, name;
        Student(String r, String n) { regNo=r; name=n; }
    }

    static final class Medical implements Serializable {
        String regNo;
        String subjectCodeOrAll; // "ALL" or subject code
        LocalDate start, end;
        String note;
        Medical(String regNo, String subjectOrAll, LocalDate start, LocalDate end, String note) {
            this.regNo = regNo; this.subjectCodeOrAll = subjectOrAll;
            this.start = start; this.end = end; this.note = note;
        }
    }

    static final class Notification implements Serializable {
        String lecturerUsername;
        String message;
        boolean read;
        LocalDateTime createdAt;
        Notification(String lecturerUsername, String message) {
            this.lecturerUsername = lecturerUsername;
            this.message = message;
            this.read = false;
            this.createdAt = LocalDateTime.now();
        }
    }

    static final class DataStore implements Serializable {
        Map<String, Student> studentsByReg = new LinkedHashMap<>();
        Set<LocalDate> holidays = new HashSet<>();
        List<Medical> medicals = new ArrayList<>();
        List<Notification> notifications = new ArrayList<>();

        // subject -> date -> regNo -> P/A
        Map<String, Map<LocalDate, Map<String, Character>>> studentAttendance = new HashMap<>();
        // subject -> date -> lecturerUsername -> P/A
        Map<String, Map<LocalDate, Map<String, Character>>> lecturerAttendance = new HashMap<>();
    }

    static final String DATA_FILE = "attendance-data.ser";
    DataStore store = new DataStore();

    private DataStore loadStoreSafe() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return new DataStore();

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof DataStore ds) return ds;
            return new DataStore();
        } catch (Exception ex) {
            // If file is bad, don't crash the app
            JOptionPane.showMessageDialog(this,
                    "Saved data file was corrupted and will be ignored.\nDelete attendance-data.ser if needed.\n\n" + ex,
                    "Data Load Warning",
                    JOptionPane.WARNING_MESSAGE);
            return new DataStore();
        }
    }

    private void saveStoreSafe() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(store);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Save failed: " + e, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // -------- UI State --------
    User currentUser;

    CardLayout cards = new CardLayout();
    JPanel root = new JPanel(cards);

    JPanel loginPanel = new JPanel(new BorderLayout());
    JPanel adminPanel = new JPanel(new BorderLayout());
    JPanel lecturerPanel = new JPanel(new BorderLayout());

    public AttendanceSystemSwing() {
        super("Student Attendance Management System (Swing)");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1050, 650);
        setLocationRelativeTo(null);

        // load data AFTER frame exists (so we can show warning popups safely)
        store = loadStoreSafe();

        buildLoginPanel();
        buildAdminPanel(); // admin panel is safe at startup

        root.add(loginPanel, "LOGIN");
        root.add(adminPanel, "ADMIN");
        // lecturerPanel will be built AFTER lecturer logs in (prevents null currentUser crash)

        setContentPane(root);
        cards.show(root, "LOGIN");
    }

    // ---------- Helpers ----------
    private static Subject subjectByCode(String code) {
        for (Subject s : SUBJECTS) if (s.code.equals(code)) return s;
        return null;
    }

    private Subject subjectForLecturer(String lecturerUsername) {
        for (Subject s : SUBJECTS) if (s.lecturerUsername.equals(lecturerUsername)) return s;
        return null;
    }

    private boolean isHoliday(LocalDate d) { return store.holidays.contains(d); }

    private String todayTimetableText() {
        DayOfWeek d = LocalDate.now().getDayOfWeek();
        if (!TIMETABLE.containsKey(d)) return "No class today (Weekend).";
        Subject sub = subjectByCode(TIMETABLE.get(d));
        return d + ": " + sub.code + " - " + sub.title + " | " + TIME_RANGE + " | Lecturer: " + sub.lecturerName;
    }

    private LocalDate parseDateOrNull(String s) {
        try { return LocalDate.parse(s.trim()); }
        catch (DateTimeParseException e) { return null; }
    }

    // ---------- LOGIN ----------
    private void buildLoginPanel() {
        JLabel title = new JLabel("Attendance Management System (Admin / Lecturer)");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JTextField userField = new JTextField(18);
        JPasswordField passField = new JPasswordField(18);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8,8,8,8);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx=0; g.gridy=0; form.add(new JLabel("Username:"), g);
        g.gridx=1; form.add(userField, g);
        g.gridx=0; g.gridy=1; form.add(new JLabel("Password:"), g);
        g.gridx=1; form.add(passField, g);

        JButton loginBtn = new JButton("Login");
        JButton resetBtn = new JButton("Reset");

        loginBtn.addActionListener(e -> {
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());

            User user = USERS.get(u);
            if (user == null || !user.password.equals(p)) {
                JOptionPane.showMessageDialog(this, "Invalid login!", "Login Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            currentUser = user;

            if (user.role == Role.ADMIN) {
                cards.show(root, "ADMIN");
            } else {
                // Build lecturer UI ONLY NOW (currentUser exists)
                lecturerPanel.removeAll();
                buildLecturerPanel();
                root.add(lecturerPanel, "LECTURER");
                cards.show(root, "LECTURER");
            }
        });

        resetBtn.addActionListener(e -> {
            userField.setText("");
            passField.setText("");
        });

        JTextArea info = new JTextArea(
                "Default logins:\n" +
                "Admin: admin / admin123\n" +
                "Lecturers: lect1012, lect1022, lect1032, lect1042, lect1062  (password: lect123)\n\n" +
                "Today: " + todayTimetableText()
        );
        info.setEditable(false);
        info.setBackground(new Color(245,245,245));
        info.setBorder(new EmptyBorder(10,10,10,10));

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(12,12,12,12));
        top.add(title, BorderLayout.NORTH);
        top.add(new JLabel("Timetable: Mon 1022 | Tue 1012 | Wed 1032 | Thu 1042 | Fri 1062 (8AM–3PM)"), BorderLayout.SOUTH);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(resetBtn);
        btns.add(loginBtn);

        loginPanel.add(top, BorderLayout.NORTH);
        loginPanel.add(form, BorderLayout.CENTER);
        loginPanel.add(info, BorderLayout.SOUTH);
        loginPanel.add(btns, BorderLayout.EAST);
    }

    // ---------- ADMIN ----------
    private void buildAdminPanel() {
        JLabel title = new JLabel("ADMIN Dashboard");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> { currentUser=null; cards.show(root, "LOGIN"); });

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(10,10,10,10));
        top.add(title, BorderLayout.WEST);
        top.add(logout, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Students", adminStudentsTab());
        tabs.addTab("Holidays", adminHolidaysTab());
        tabs.addTab("Medical", adminMedicalTab());
        tabs.addTab("Reports", adminReportsTab());

        adminPanel.add(top, BorderLayout.NORTH);
        adminPanel.add(tabs, BorderLayout.CENTER);
    }

    private JPanel adminStudentsTab() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Reg No", "Name"}, 0);
        JTable table = new JTable(model);
        refreshStudentsModel(model);

        JTextField reg = new JTextField(12);
        JTextField name = new JTextField(20);
        JButton add = new JButton("Add Student");
        JButton delete = new JButton("Delete Selected");
        JButton save = new JButton("Save");

        add.addActionListener(e -> {
            String r = reg.getText().trim();
            String n = name.getText().trim();
            if (r.isEmpty() || n.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter Reg No and Name."); return; }
            if (store.studentsByReg.containsKey(r)) { JOptionPane.showMessageDialog(this, "Reg No already exists."); return; }
            store.studentsByReg.put(r, new Student(r, n));
            refreshStudentsModel(model);
            reg.setText(""); name.setText("");
        });

        delete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String r = (String) model.getValueAt(row, 0);
            store.studentsByReg.remove(r);
            for (var subj : store.studentAttendance.values()) {
                for (var dateMap : subj.values()) dateMap.remove(r);
            }
            store.medicals.removeIf(m -> m.regNo.equals(r));
            refreshStudentsModel(model);
        });

        save.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Reg No:")); form.add(reg);
        form.add(new JLabel("Name:")); form.add(name);
        form.add(add); form.add(delete); form.add(save);

        JPanel p = new JPanel(new BorderLayout());
        p.add(form, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshStudentsModel(DefaultTableModel model) {
        model.setRowCount(0);
        for (Student s : store.studentsByReg.values()) model.addRow(new Object[]{s.regNo, s.name});
    }

    private JPanel adminHolidaysTab() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Date (YYYY-MM-DD)"}, 0);
        JTable table = new JTable(model);
        refreshHolidaysModel(model);

        JTextField date = new JTextField(12);
        JButton add = new JButton("Add Holiday");
        JButton remove = new JButton("Remove Selected");
        JButton save = new JButton("Save");

        add.addActionListener(e -> {
            LocalDate d = parseDateOrNull(date.getText());
            if (d == null) { JOptionPane.showMessageDialog(this, "Invalid date. Use YYYY-MM-DD"); return; }
            store.holidays.add(d);
            refreshHolidaysModel(model);
            date.setText("");
        });

        remove.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            LocalDate d = LocalDate.parse((String) model.getValueAt(row, 0));
            store.holidays.remove(d);
            refreshHolidaysModel(model);
        });

        save.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Holiday Date:"));
        top.add(date);
        top.add(add); top.add(remove); top.add(save);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshHolidaysModel(DefaultTableModel model) {
        model.setRowCount(0);
        List<LocalDate> dates = new ArrayList<>(store.holidays);
        dates.sort(Comparator.naturalOrder());
        for (LocalDate d : dates) model.addRow(new Object[]{d.toString()});
    }

    private JPanel adminMedicalTab() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Reg No", "Subject", "Start", "End", "Note"}, 0);
        JTable table = new JTable(model);
        refreshMedicalsModel(model);

        JComboBox<String> studentCombo = new JComboBox<>();
        refreshStudentCombo(studentCombo);

        JComboBox<String> subjectCombo = new JComboBox<>();
        subjectCombo.addItem("ALL");
        for (Subject s : SUBJECTS) subjectCombo.addItem(s.code);

        JTextField start = new JTextField(10);
        JTextField end = new JTextField(10);
        JTextField note = new JTextField(18);

        JButton add = new JButton("Add Medical + Notify");
        JButton delete = new JButton("Delete Selected");
        JButton save = new JButton("Save");

        add.addActionListener(e -> {
            if (studentCombo.getItemCount() == 0) { JOptionPane.showMessageDialog(this, "Add students first."); return; }
            String regNo = (String) studentCombo.getSelectedItem();
            String subj = (String) subjectCombo.getSelectedItem();

            LocalDate sDate = parseDateOrNull(start.getText());
            LocalDate eDate = parseDateOrNull(end.getText());
            if (sDate == null || eDate == null) { JOptionPane.showMessageDialog(this, "Invalid dates. Use YYYY-MM-DD."); return; }
            if (eDate.isBefore(sDate)) { JOptionPane.showMessageDialog(this, "End date cannot be before start date."); return; }

            Medical m = new Medical(regNo, subj, sDate, eDate, note.getText().trim());
            store.medicals.add(m);

            // notify lecturers
            if ("ALL".equals(subj)) {
                for (Subject sub : SUBJECTS) store.notifications.add(new Notification(sub.lecturerUsername, buildMedicalMessage(regNo, m)));
            } else {
                Subject sub = subjectByCode(subj);
                if (sub != null) store.notifications.add(new Notification(sub.lecturerUsername, buildMedicalMessage(regNo, m)));
            }

            refreshMedicalsModel(model);
            start.setText(""); end.setText(""); note.setText("");
            JOptionPane.showMessageDialog(this, "Medical added and lecturers notified.");
        });

        delete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            String regNo = (String) model.getValueAt(row, 0);
            String subj = (String) model.getValueAt(row, 1);
            String s = (String) model.getValueAt(row, 2);
            String en = (String) model.getValueAt(row, 3);

            store.medicals.removeIf(m ->
                    m.regNo.equals(regNo) &&
                    m.subjectCodeOrAll.equals(subj) &&
                    m.start.toString().equals(s) &&
                    m.end.toString().equals(en)
            );
            refreshMedicalsModel(model);
        });

        save.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JButton refreshBtn = new JButton("Refresh Students");
        refreshBtn.addActionListener(e -> refreshStudentCombo(studentCombo));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Student:")); top.add(studentCombo); top.add(refreshBtn);
        top.add(new JLabel("Subject:")); top.add(subjectCombo);
        top.add(new JLabel("Start:")); top.add(start);
        top.add(new JLabel("End:")); top.add(end);
        top.add(new JLabel("Note:")); top.add(note);
        top.add(add); top.add(delete); top.add(save);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private void refreshStudentCombo(JComboBox<String> combo) {
        combo.removeAllItems();
        for (Student s : store.studentsByReg.values()) combo.addItem(s.regNo);
    }

    private String buildMedicalMessage(String regNo, Medical m) {
        String name = store.studentsByReg.containsKey(regNo) ? store.studentsByReg.get(regNo).name : "";
        return "Medical submitted for " + regNo + " - " + name +
                " | " + m.start + " to " + m.end +
                " | Subject: " + m.subjectCodeOrAll +
                " | Adds +5% (max 100%).";
    }

    private void refreshMedicalsModel(DefaultTableModel model) {
        model.setRowCount(0);
        for (Medical m : store.medicals) {
            model.addRow(new Object[]{m.regNo, m.subjectCodeOrAll, m.start.toString(), m.end.toString(), m.note});
        }
    }

    private JPanel adminReportsTab() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JButton refresh = new JButton("Generate Report");
        refresh.addActionListener(e -> area.setText(generateFullStudentReport()));

        JButton save = new JButton("Save Data");
        save.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh); top.add(save);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);

        area.setText(generateFullStudentReport());
        return p;
    }

    // ---------- LECTURER (built only after login) ----------
    private void buildLecturerPanel() {
        JLabel title = new JLabel("LECTURER Dashboard (" + currentUser.username + ")");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> { currentUser=null; cards.show(root, "LOGIN"); });

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(10,10,10,10));
        top.add(title, BorderLayout.WEST);
        top.add(logout, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Today", lecturerTodayTab());
        tabs.addTab("Student Attendance", lecturerAttendanceTab());
        tabs.addTab("Lecturer Attendance", lecturerLecturerAttendanceTab());
        tabs.addTab("Notifications", lecturerNotificationsTab());
        tabs.addTab("Summary", lecturerSummaryTab());

        lecturerPanel.setLayout(new BorderLayout());
        lecturerPanel.add(top, BorderLayout.NORTH);
        lecturerPanel.add(tabs, BorderLayout.CENTER);
        lecturerPanel.revalidate();
        lecturerPanel.repaint();
    }

    private JPanel lecturerTodayTab() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setBackground(new Color(245,245,245));
        area.setBorder(new EmptyBorder(10,10,10,10));
        Subject me = subjectForLecturer(currentUser.username);
        area.setText("Today: " + todayTimetableText() + "\n\nYou are: " +
                (me == null ? "Unknown" : me.lecturerName + " | Subject: " + me.code));
        return new JPanel(new BorderLayout()) {{ add(area, BorderLayout.CENTER); }};
    }

    private JPanel lecturerAttendanceTab() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Reg No", "Name", "Status (P/A)"}, 0);
        JTable table = new JTable(model);

        JTextField dateField = new JTextField(10);
        dateField.setText(LocalDate.now().toString());

        JButton load = new JButton("Load Students");
        JButton markAllP = new JButton("Mark All Present");
        JButton saveAttendance = new JButton("Save Attendance");
        JButton saveData = new JButton("Save Data");

        load.addActionListener(e -> {
            model.setRowCount(0);
            for (Student s : store.studentsByReg.values()) model.addRow(new Object[]{s.regNo, s.name, "P"});
        });

        markAllP.addActionListener(e -> {
            for (int i=0;i<model.getRowCount();i++) model.setValueAt("P", i, 2);
        });

        saveAttendance.addActionListener(e -> {
            Subject sub = subjectForLecturer(currentUser.username);
            if (sub == null) { JOptionPane.showMessageDialog(this, "Subject not assigned."); return; }

            LocalDate d = parseDateOrNull(dateField.getText());
            if (d == null) { JOptionPane.showMessageDialog(this, "Invalid date (YYYY-MM-DD)."); return; }
            if (isHoliday(d)) { JOptionPane.showMessageDialog(this, "This date is a HOLIDAY. No attendance allowed."); return; }

            store.studentAttendance.putIfAbsent(sub.code, new HashMap<>());
            store.studentAttendance.get(sub.code).putIfAbsent(d, new HashMap<>());
            Map<String, Character> map = store.studentAttendance.get(sub.code).get(d);

            for (int i=0;i<model.getRowCount();i++) {
                String reg = (String) model.getValueAt(i, 0);
                String status = String.valueOf(model.getValueAt(i, 2)).trim().toUpperCase();
                map.put(reg, status.startsWith("A") ? 'A' : 'P');
            }

            JOptionPane.showMessageDialog(this, "Student attendance saved for " + sub.code + " on " + d);
        });

        saveData.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Date:")); top.add(dateField);
        top.add(load); top.add(markAllP); top.add(saveAttendance); top.add(saveData);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel lecturerLecturerAttendanceTab() {
        JTextField dateField = new JTextField(10);
        dateField.setText(LocalDate.now().toString());
        JComboBox<String> status = new JComboBox<>(new String[]{"P", "A"});
        JButton save = new JButton("Save Lecturer Attendance");
        JButton saveData = new JButton("Save Data");

        save.addActionListener(e -> {
            Subject sub = subjectForLecturer(currentUser.username);
            if (sub == null) { JOptionPane.showMessageDialog(this, "Subject not assigned."); return; }

            LocalDate d = parseDateOrNull(dateField.getText());
            if (d == null) { JOptionPane.showMessageDialog(this, "Invalid date (YYYY-MM-DD)."); return; }
            if (isHoliday(d)) { JOptionPane.showMessageDialog(this, "This date is a HOLIDAY. No attendance allowed."); return; }

            store.lecturerAttendance.putIfAbsent(sub.code, new HashMap<>());
            store.lecturerAttendance.get(sub.code).putIfAbsent(d, new HashMap<>());
            store.lecturerAttendance.get(sub.code).get(d).put(currentUser.username, ((String)status.getSelectedItem()).charAt(0));

            JOptionPane.showMessageDialog(this, "Lecturer attendance saved.");
        });

        saveData.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Date:")); top.add(dateField);
        top.add(new JLabel("Status:")); top.add(status);
        top.add(save); top.add(saveData);

        return new JPanel(new BorderLayout()) {{ add(top, BorderLayout.NORTH); }};
    }

    private JPanel lecturerNotificationsTab() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Time", "Status", "Message"}, 0);
        JTable table = new JTable(model);

        JButton refresh = new JButton("Refresh");
        JButton markAllRead = new JButton("Mark All Read");
        JButton saveData = new JButton("Save Data");

        refresh.addActionListener(e -> refreshNotificationsModel(model));
        markAllRead.addActionListener(e -> {
            for (Notification n : store.notifications) if (n.lecturerUsername.equals(currentUser.username)) n.read = true;
            refreshNotificationsModel(model);
        });
        saveData.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh); top.add(markAllRead); top.add(saveData);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        refresh.doClick();
        return p;
    }

    private void refreshNotificationsModel(DefaultTableModel model) {
        model.setRowCount(0);
        List<Notification> list = new ArrayList<>();
        for (Notification n : store.notifications) if (n.lecturerUsername.equals(currentUser.username)) list.add(n);
        list.sort((a,b)-> b.createdAt.compareTo(a.createdAt));
        for (Notification n : list) model.addRow(new Object[]{n.createdAt.toString(), n.read ? "READ" : "NEW", n.message});
    }

    private JPanel lecturerSummaryTab() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton refresh = new JButton("Generate Summary");
        refresh.addActionListener(e -> area.setText(generateLecturerSubjectSummary()));
        JButton saveData = new JButton("Save Data");
        saveData.addActionListener(e -> { saveStoreSafe(); JOptionPane.showMessageDialog(this, "Saved."); });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(refresh); top.add(saveData);

        JPanel p = new JPanel(new BorderLayout());
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        refresh.doClick();
        return p;
    }

    // ---------- Reports ----------
    private int totalSessionsForSubject(String subjectCode) {
        Map<LocalDate, Map<String, Character>> dateMap = store.studentAttendance.getOrDefault(subjectCode, Map.of());
        int count = 0;
        for (LocalDate d : dateMap.keySet()) if (!isHoliday(d)) count++;
        return count;
    }

    private int presentCount(String subjectCode, String regNo) {
        Map<LocalDate, Map<String, Character>> dateMap = store.studentAttendance.getOrDefault(subjectCode, Map.of());
        int p = 0;
        for (var entry : dateMap.entrySet()) {
            if (isHoliday(entry.getKey())) continue;
            Character st = entry.getValue().get(regNo);
            if (st != null && st == 'P') p++;
        }
        return p;
    }

    private boolean studentHasMedicalForSubject(String regNo, String subjectCode) {
        for (Medical m : store.medicals) {
            if (!m.regNo.equals(regNo)) continue;
            if ("ALL".equals(m.subjectCodeOrAll) || subjectCode.equals(m.subjectCodeOrAll)) return true;
        }
        return false;
    }

    private double attendancePercentWithMedical(String subjectCode, String regNo) {
        int total = totalSessionsForSubject(subjectCode);
        if (total == 0) return 0.0;
        double percent = presentCount(subjectCode, regNo) * 100.0 / total;
        if (studentHasMedicalForSubject(regNo, subjectCode)) percent = Math.min(100.0, percent + 5.0);
        return percent;
    }

    private String generateFullStudentReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("FULL STUDENT REPORT (Medical +5%, Eligibility >=80%)\n");
        sb.append("------------------------------------------------------\n\n");

        if (store.studentsByReg.isEmpty()) {
            sb.append("No students found. Admin -> Students -> Add Student.\n");
            return sb.toString();
        }

        for (Student st : store.studentsByReg.values()) {
            sb.append("Student: ").append(st.regNo).append(" - ").append(st.name).append("\n");
            sb.append(String.format("%-10s %-30s %8s %8s %12s %12s %12s\n",
                    "Subject", "Title", "Present", "Total", "%", "%+Med", "Eligible"));

            for (Subject sub : SUBJECTS) {
                int total = totalSessionsForSubject(sub.code);
                int present = presentCount(sub.code, st.regNo);
                double raw = (total == 0) ? 0.0 : present * 100.0 / total;
                double withMed = attendancePercentWithMedical(sub.code, st.regNo);
                boolean eligible = withMed >= 80.0;

                sb.append(String.format("%-10s %-30s %8d %8d %11.2f%% %11.2f%% %12s\n",
                        sub.code, sub.title, present, total, raw, withMed, eligible ? "YES" : "NO"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String generateLecturerSubjectSummary() {
        Subject sub = subjectForLecturer(currentUser.username);
        if (sub == null) return "No subject assigned.\n";

        StringBuilder sb = new StringBuilder();
        sb.append("LECTURER SUMMARY for ").append(sub.code).append(" - ").append(sub.title).append("\n");
        sb.append("Lecturer: ").append(sub.lecturerName).append("\n");
        sb.append("Medical adds +5% (max 100%). Eligible if >=80%.\n\n");

        int total = totalSessionsForSubject(sub.code);
        sb.append("Total Sessions (excluding holidays): ").append(total).append("\n\n");

        sb.append(String.format("%-12s %-25s %8s %8s %12s %12s %10s\n",
                "RegNo", "Name", "Present", "Total", "%", "%+Med", "Eligible"));

        for (Student st : store.studentsByReg.values()) {
            int present = presentCount(sub.code, st.regNo);
            double raw = (total == 0) ? 0.0 : present * 100.0 / total;
            double withMed = attendancePercentWithMedical(sub.code, st.regNo);
            boolean eligible = withMed >= 80.0;

            sb.append(String.format("%-12s %-25s %8d %8d %11.2f%% %11.2f%% %10s\n",
                    st.regNo, st.name, present, total, raw, withMed, eligible ? "YES" : "NO"));
        }
        return sb.toString();
    }

    // ---------- MAIN (with crash popup) ----------
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "App Crash", JOptionPane.ERROR_MESSAGE);
        });

        SwingUtilities.invokeLater(() -> {
            try {
                AttendanceSystemSwing app = new AttendanceSystemSwing();
                app.setVisible(true);
            } catch (Throwable e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.toString(), "Startup Crash", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
