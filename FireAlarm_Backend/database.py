import psycopg2
from psycopg2 import pool
from psycopg2.extras import RealDictCursor
import os
from dotenv import load_dotenv

load_dotenv()

db_params = {
    "host": os.environ["PGHOST"],
    "database": os.environ["PGDATABASE"],
    "user": os.environ["PGUSER"],
    "password": os.environ["PGPASSWORD"],
    "port": int(os.environ.get("PGPORT", 5432)),
    "sslmode": os.environ.get("PGSSLMODE", "require"),
}

# Tạo 1 hồ chứa kết nối (pool) - duy trì từ 1 đến 10 kết nối sẵn sàng
# Tạo sẵn 10 kết nối   : -> khi cần thì lấy ra dùng -> dùng xong trả lại pool ( tối đa 10 kết nối cùng lúc)
# k cần kết nối lại mỗi lần -  db k bị spam kết nối  - có event-> laays ra dùng

try:
    connection_pool = psycopg2.pool.SimpleConnectionPool(1, 10, **db_params)
    print("Đã thiết lập kết nối pool thành công")
except Exception as e:
    print(f"Lỗi khởi tạo pool: {e}")

def get_db_connection():
    # Lấy 1 kết nối đang raảnh từ pool thay vì tạo mới
    return connection_pool.getconn()

def release_db_connection(conn):
    #Trả lại kết nối cho pool để ng khác dùng
    connection_pool.putconn(conn)