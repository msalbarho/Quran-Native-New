import os
import sqlite3

root = r"d:\HolyQuran-Native\app\src\main\assets\db"
for name in os.listdir(root):
    path = os.path.join(root, name)
    if not os.path.isfile(path) or name.startswith("."):
        continue
    print("=" * 80)
    print(name, os.path.getsize(path))
    con = sqlite3.connect(path)
    cur = con.cursor()
    tables = cur.execute(
        """
        SELECT name, type, sql
        FROM sqlite_master
        WHERE type IN ('table', 'index', 'view')
          AND name NOT LIKE 'sqlite_%'
        ORDER BY type, name
        """
    ).fetchall()
    for table_name, table_type, sql in tables:
        print(f"--- {table_type}: {table_name}")
        if table_type == "table":
            cols = cur.execute(f"PRAGMA table_info({table_name})").fetchall()
            for col in cols:
                print(
                    f"    {col[1]:24} {col[2]:12} pk={col[5]} notnull={col[3]} default={col[4]}"
                )
            indexes = cur.execute(f"PRAGMA index_list({table_name})").fetchall()
            for index in indexes:
                print(f"    INDEX {index}")
        else:
            print("   ", (sql or "")[:240])
    con.close()
