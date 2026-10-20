# Nhóm 06 – Ứng dụng Chat Nhóm (Group Chat)

Học phần **Lập trình mạng máy tính** – Lớp 23DTHB1 – HUTECH.
Mô hình Client–Server, Socket TCP, server đa luồng, quản lý phòng, lưu lịch sử tin nhắn vào SQL Server.

## Cấu trúc

```
Nhom6-LTMMT/
├── chat-common/   Giao thức dùng chung (Protocol, ChatMessage, RoomInfo)
├── chat-server/   Server đa luồng + công cụ TestClient
├── chat-client/   Client Swing
├── database/      schema.sql
└── docs/          protocol.md (đọc trước khi code!)
```

## Phân công

| Thành viên | Phụ trách | Vị trí code |
|---|---|---|
| **Tạ Tuấn Phát** (NT) | Giao thức, lõi Server đa luồng, quản lý phòng, broadcast; review & merge | `chat-common`, `chat-server/.../server` |
| **Trần Lãng** | Mạng phía Client; JDBC + `JdbcMessageStore` (lưu/đọc lịch sử) | `chat-client/.../client/net`, `chat-server/.../server/store` |
| **Nguyễn Đình Quốc Bảo** | `schema.sql`; giao diện quản trị Server; kịch bản kiểm thử | `database/`, `chat-server/.../server/ui` |
| **Nguyễn Phan Hồng Huy** | Giao diện Client Swing; trình bày báo cáo | `chat-client/.../client/ui` |

**Cổng nối giữa các phần:**
- Bảo gắn giao diện quản trị qua `ServerListener` (nhớ dùng `SwingUtilities.invokeLater`).
- Lãng viết `JdbcMessageStore implements MessageStore`, rồi đổi 1 dòng trong `ServerMain`.
- Huy làm form, có các hàm như `appendMessage(...)`, `updateRoomList(...)` để Lãng gọi vào.

## Chạy thử (NetBeans)

1. **File → Open Project** → chọn thư mục `Nhom6-LTMMT` → tick **Open Required Projects**.
2. Chuột phải project cha **Nhom6-LTMMT** → **Clean and Build** (lần đầu cần mạng để tải Maven).
3. **Server có giao diện:** chuột phải `chat-server/.../server/ui/ServerAdminMain.java` → **Run File** → bấm **Start**.
   (Hoặc chạy console: `chat-server/.../server/ServerMain.java`.)
4. **Client giao diện:** chuột phải `chat-client/.../client/ClientMain.java` → **Run File**, nhập IP server, cổng 5000, tên hiển thị → **Kết nối**.
   Chạy `ClientMain` thêm lần nữa để mở client thứ 2 và chat qua lại.
5. Máy khác trong mạng LAN: nhập IP của máy chạy server (xem bằng `ipconfig`), nhớ cho phép Java qua Windows Firewall.

Lưu lịch sử vào SQL Server: chạy `database/schema.sql` trong SSMS, bật TCP/IP cho instance SQL Server,
copy `db.properties.example` thành `db.properties` và sửa url/user/password. Không có file này thì server tự lưu tạm trong RAM.

Lệnh trong cửa sổ server: `status`, `stop`.

## Quy trình GitHub

1. Mỗi lần ngồi code: **Pull** `main` mới nhất.
2. Tạo nhánh riêng: `feature/<ten-phan>` (vd `feature/client-ui`, `feature/jdbc`).
3. Commit thường xuyên, message rõ ràng (vd `Them LoginFrame`).
4. Push nhánh → tạo **Pull Request** vào `main` → Phát review và merge.
5. **Không** push thẳng lên `main`, **không** sửa file của người khác khi chưa báo.
6. **Không** đưa mật khẩu SQL Server lên GitHub (để trong `db.properties`, đã nằm trong `.gitignore`).

## Tiến độ (03/10 – 28/11/2026)

| Tuần | Việc |
|---|---|
| 1–2 | Chốt giao thức, CSDL, phác thảo giao diện |
| 3–5 | Mỗi người code phần của mình |
| 6–7 | Tích hợp, kiểm thử nhiều client, chạy qua các mạng khác nhau |
| 8 | Hoàn thiện, viết báo cáo, chuẩn bị demo |
