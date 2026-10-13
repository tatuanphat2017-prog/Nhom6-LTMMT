# Kịch bản kiểm thử – Nhóm 06 (Ứng dụng Chat Nhóm)

> Người phụ trách: **Nguyễn Đình Quốc Bảo**.
> Công cụ: `ServerAdminMain` (giao diện quản trị) + `TestClient` / `ConsoleDemo` (client console). Mở 2–3 client để quan sát nhau.
> Ký hiệu: **A** = client thứ nhất, **B** = client thứ hai. Lệnh gõ trong `TestClient`.

**Cột "Kết quả thực tế"** đã điền theo lần chạy kiểm thử tự động ngày 13/10/2026 (server thật, client gửi đúng gói tin của giao thức).
Khi demo, nhóm chạy lại bằng tay theo cột "Bước làm", chụp ảnh và sửa cột này nếu khác. Riêng **TC18** (rút mạng thật giữa 2 máy) và **TC20** (với SQL Server thật) bắt buộc chạy lại bằng tay.

| STT | Tình huống | Bước làm | Kết quả mong đợi | Kết quả thực tế |
|---|---|---|---|---|
| 1 | Đăng nhập hợp lệ | Start server. A: `/login Lang` | A nhận `LOGIN_OK|Lang` rồi `ROOM_LIST`. Giao diện quản trị: "Người online" có Lang, "Kết nối" = 1 | Đạt. A nhận `LOGIN_OK|Lang` ; `ROOM_LIST|Phong chung:0` |
| 2 | Tên đăng nhập trùng (khác hoa/thường) | A đã đăng nhập "Lang". B: `/login lang` | B nhận `ERROR|NAME_TAKEN`, vẫn ở màn hình đăng nhập | Đạt. `ERROR|NAME_TAKEN|Ten 'lang' dang co nguoi su dung` |
| 3 | Tên sai định dạng | B: `/login Lang Tran` (có khoảng trắng); `/login` + 21 ký tự | Cả hai lần nhận `ERROR|INVALID_NAME` | Đạt. Cả 2 lần nhận `ERROR|INVALID_NAME|Ten 1-20 ky tu, chi gom chu, so, _ . - (khong khoang trang)` |
| 4 | Gửi lệnh khi chưa đăng nhập | Client mới kết nối: `/join Phong chung` | `ERROR|NOT_LOGGED_IN` | Đạt. `ERROR|NOT_LOGGED_IN|Ban can dang nhap truoc` |
| 5 | Đăng nhập lần thứ hai | A đã đăng nhập: `/login Lang2` | `ERROR|ALREADY_LOGGED_IN`, tên vẫn là Lang | Đạt. `ERROR|ALREADY_LOGGED_IN|Ban da dang nhap voi ten Lang` |
| 6 | Tạo phòng mới (tiếng Việt) | A: `/create Phòng AI` | A nhận `ROOM_CREATED`. Mọi người nhận `ROOM_LIST` có "Phòng AI:0". Bảng phòng trên giao diện quản trị thêm 1 dòng | Đạt. A nhận `ROOM_CREATED|Phòng AI` ; B nhận `ROOM_LIST|Phong chung:0|Phòng AI:0` |
| 7 | Tạo phòng trùng tên | A: `/create phòng ai` | `ERROR|ROOM_EXISTS` (không phân biệt hoa/thường) | Đạt. `ERROR|ROOM_EXISTS|Phong 'phòng ai' da ton tai` |
| 8 | Tên phòng sai định dạng | A: `/create` + 31 ký tự | `ERROR|INVALID_ROOM` | Đạt. `ERROR|INVALID_ROOM|Ten phong 1-30 ky tu, ...` |
| 9 | Vào phòng | A: `/join phong chung` (viết thường). Sau đó B: `/join Phong chung` | A nhận lần lượt `JOINED` → các `HISTORY_ITEM` → `HISTORY_END` → `MEMBERS`. Khi B vào, A nhận `USER_JOINED|Phong chung|Huy`. Số người trong bảng phòng tăng | Đạt. `JOINED|Phong chung` ; `HISTORY_END|Phong chung|0` ; `MEMBERS|Phong chung|Lang` ; A nhận `USER_JOINED|Phong chung|Huy` |
| 10 | Vào phòng không tồn tại | A: `/join Khong co` | `ERROR|ROOM_NOT_FOUND` | Đạt. `ERROR|ROOM_NOT_FOUND|Khong tim thay phong 'Khong co'` |
| 11 | Vào lại phòng đang ở | A đang ở "Phong chung": `/join Phong chung` | `ERROR|ALREADY_IN_ROOM` | Đạt. `ERROR|ALREADY_IN_ROOM|Ban dang o trong phong nay` |
| 12 | Chuyển phòng | A đang ở "Phong chung": `/join Phòng AI` | A nhận `JOINED|Phòng AI`. B (ở Phong chung) nhận `USER_LEFT` của A. Mỗi người chỉ ở 1 phòng | Đạt. A nhận `JOINED|Phòng AI` ; B nhận `USER_LEFT|Phong chung|Lang` |
| 13 | Rời phòng | A: `/leave` | A nhận `LEFT`. Người cùng phòng nhận `USER_LEFT`. Số người trong bảng phòng giảm | Đạt. A nhận `LEFT|Phòng AI` |
| 14 | Gửi tin khi chưa vào phòng | A chưa vào phòng nào, gửi gói `MSG|Phong chung|hello` (`/raw MSG|Phong chung|hello`) | `ERROR|NOT_IN_ROOM` | Đạt. `ERROR|NOT_IN_ROOM|Ban phai vao phong truoc khi gui tin` |
| 15 | Tin nhắn rỗng | A trong phòng, gửi tin chỉ có khoảng trắng (`/raw MSG|Phong chung|   `) | `ERROR|EMPTY_MESSAGE`, không ai nhận tin | Đạt. `ERROR|EMPTY_MESSAGE|Tin nhan trong` |
| 16 | Tin nhắn quá dài | A gửi tin 1001 ký tự | `ERROR|MESSAGE_TOO_LONG` | Đạt. `ERROR|MESSAGE_TOO_LONG|Tin nhan toi da 1000 ky tu` |
| 17 | Tin có ký tự đặc biệt và tiếng Việt | A gửi: `Chào | a\b` + xuống dòng + `dòng 2` (qua giao diện của Huy; client console không gõ được xuống dòng) | B nhận đúng nguyên văn, kể cả dấu `|`, `\`, xuống dòng và chữ có dấu | Đạt. Gói tin `MSG|Phong chung|Lang|...|Chào \p a\\b\ndòng 2`, giải mã ra đúng nguyên văn |
| 18 | Rút mạng / tắt client giữa chừng | A và B cùng phòng. Tắt hẳn cửa sổ B (hoặc rút dây mạng / tắt Wi-Fi máy B) không đăng xuất | A nhận `USER_LEFT` của B. Giao diện quản trị: B biến mất khỏi "Người online", số kết nối giảm. B đăng nhập lại được bằng tên cũ | Đạt (đóng socket đột ngột). A nhận `USER_LEFT|Phong chung|Huy` ; online còn `[Lang]` ; B đăng nhập lại nhận `LOGIN_OK|Huy`. *Cần thử lại bằng rút mạng thật giữa 2 máy* |
| 19 | Đăng xuất và dừng server | A: `/quit`. Sau đó bấm **Stop** trên giao diện quản trị khi còn client B | A nhận `BYE|Tam biet` rồi bị đóng kết nối. Khi Stop, B nhận `BYE|Server dung hoat dong`. Danh sách online và bảng phòng được xóa | Đạt. `BYE|Tam biet` ; `BYE|Server dung hoat dong`, kết nối đóng sau BYE |
| 20 | Tắt server mở lại còn lịch sử | Có `db.properties` (SQL Server). Chat vài câu → Stop → Start → client mới `/join Phong chung` | Phòng đã tạo vẫn còn trong `ROOM_LIST`. Client nhận lại các tin cũ qua `HISTORY_ITEM` theo thứ tự cũ → mới | Đạt (CSDL mô phỏng chế độ SQL Server). `ROOM_LIST|Phong chung:0|Phòng AI:0` ; `HISTORY_ITEM|...|Chào \p a\\b\ndòng 2` ; `HISTORY_END|Phong chung|1`. *Cần chạy lại với SQL Server thật* |
