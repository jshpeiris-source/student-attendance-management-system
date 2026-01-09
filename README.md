# Student Attendance Management System (Java Swing)

## Description

This project is a **Java Swing-based desktop application** designed to manage the attendance of students and lecturers. It provides functionalities such as:

- **Role-based access**: Only **Admin** and **Lecturers** can log in.
- **Attendance recording**: For both students and lecturers, based on a fixed weekly timetable.
- **Medical leave handling**: Admins can add medical records for students, which adjusts attendance by 5%.
- **Holiday management**: Admins can define holidays, which are excluded from attendance calculations.
- **Exam eligibility calculation**: Students must have at least 80% attendance to be eligible for exams.

This application uses **file serialization** (`attendance-data.ser`) for offline data storage and is designed for **offline usage**.

## Features

- **Admin Dashboard**:
  - Add/remove students.
  - Manage holidays.
  - Add medical records and notify lecturers.
  - View detailed attendance and eligibility reports.
  
- **Lecturer Dashboard**:
  - View subject timetable.
  - Mark student attendance.
  - Mark their own attendance.
  - View medical leave notifications.
  - View student attendance summary.

## Technologies Used

- **Java Swing** for GUI
- **File Serialization** for data persistence
- **Java 8+** (using Java's `LocalDate`, `LocalDateTime`, etc.)

## How to Run

1. Clone the repository:
    ```bash
    git clone https://github.com/yourusername/student-attendance-management-system.git
    ```

2. Open the `AttendanceSystemSwing.java` file in your preferred Java IDE (e.g., IntelliJ IDEA, Eclipse, VS Code).

3. Compile and run the Java application:
    ```bash
    javac AttendanceSystemSwing.java
    java AttendanceSystemSwing
    ```

4. Follow the on-screen prompts for Admin or Lecturer login.

## Login Credentials

### Admin:
- Username: `admin`
- Password: `admin123`

### Lecturers:
- Username: `lect1012`, `lect1022`, `lect1032`, `lect1042`, `lect1062`
- Password: `lect123`

## Future Enhancements

- Add a **student login** portal.
- **Database Integration** (e.g., MySQL).
- **Web version** for remote access.
- **PDF Export** of reports.
- **Email Notifications** for medical leave and attendance updates.


