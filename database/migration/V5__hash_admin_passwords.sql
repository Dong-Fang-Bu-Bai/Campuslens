UPDATE admin_user
SET password_hash = 'pbkdf2$120000$Q2FtcHVzTGVuc0FkbWluU2FsdDIwMjY=$LEPlmZ9ZoM5rUib2xR2wp3rGVJ/FbI0XSoMtzZUIrQI='
WHERE username = 'admin';

UPDATE app_user
SET password_hash = 'pbkdf2$120000$Q2FtcHVzTGVuc0FkbWluU2FsdDIwMjY=$LEPlmZ9ZoM5rUib2xR2wp3rGVJ/FbI0XSoMtzZUIrQI='
WHERE username = 'admin';
