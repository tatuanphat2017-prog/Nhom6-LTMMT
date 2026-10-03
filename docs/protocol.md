# Giao thức Chat Nhóm – Nhóm 06

> Người phụ trách: **Tạ Tuấn Phát**. Code tương ứng: `chat-common/.../common/Protocol.java`.
> Muốn đổi giao thức → báo cả nhóm trước, sửa file này và `Protocol.java` cùng lúc.

## 1. Quy tắc chung

- Kết nối **TCP**, cổng mặc định **5000**, mã hóa **UTF-8**.
- Mỗi gói tin là **một dòng** kết thúc bằng `\n`, dạng: `LỆNH|trường1|trường2|...`
- Nội dung trường được mã hóa (dùng `Protocol.escape/unescape`, **không tự xử lý tay**):

| Ký tự gốc | Ghi thành |
|---|---|
| `\` | `\\` |
| `\|` | `\p` |
| xuống dòng | `\n` |

- Tạo gói tin: `Protocol.build(Protocol.MSG, "Phòng A", "xin chào")`
- Đọc gói tin: `String[] p = Protocol.parse(line);` → `p[0]` là lệnh, `p[1]...` là các trường.
- Thời gian (`thoiGian`) = milliseconds (`System.currentTimeMillis()`), **do server gán**.
- Mỗi người **chỉ ở 1 phòng** tại một thời điểm. JOIN phòng mới sẽ tự rời phòng cũ.

## 2. Client → Server

| Lệnh | Cú pháp | Ghi chú |
|---|---|---|
| Đăng nhập | `LOGIN\|ten` | 1–20 ký tự: chữ (có dấu), số, `_ . -`, không khoảng trắng |
| Danh sách phòng | `LIST_ROOMS` | |
| Tạo phòng | `CREATE_ROOM\|phong` | 1–30 ký tự, được có khoảng trắng ở giữa |
| Vào phòng | `JOIN\|phong` | Không phân biệt hoa/thường |
| Rời phòng | `LEAVE\|phong` | |
| Gửi tin | `MSG\|phong\|noiDung` | Tối đa 1000 ký tự, phải đang ở trong phòng |
| Xem lịch sử | `HISTORY\|phong\|soLuong` | 1–200, mặc định 50 |
| Thành viên phòng | `MEMBERS\|phong` | |
| Đăng xuất | `LOGOUT` | Server trả `BYE` rồi đóng kết nối |

Chưa `LOGIN` thì mọi lệnh khác (trừ `LOGOUT`) đều bị trả `ERROR|NOT_LOGGED_IN`.

## 3. Server → Client

| Gói tin | Cú pháp | Khi nào |
|---|---|---|
| `LOGIN_OK` | `LOGIN_OK\|ten` | Đăng nhập thành công (ngay sau đó có `ROOM_LIST`) |
| `ROOM_LIST` | `ROOM_LIST\|phong1:soNguoi\|phong2:soNguoi...` | Sau LOGIN, LIST_ROOMS, và **mỗi khi phòng/số người thay đổi** (gửi cho mọi người) |
| `ROOM_CREATED` | `ROOM_CREATED\|phong` | Tạo phòng thành công |
| `JOINED` | `JOINED\|phong` | Vào phòng thành công |
| `HISTORY_ITEM` | `HISTORY_ITEM\|phong\|nguoiGui\|thoiGian\|noiDung` | Từng tin lịch sử, cũ → mới |
| `HISTORY_END` | `HISTORY_END\|phong\|soLuong` | Kết thúc lịch sử |
| `MEMBERS` | `MEMBERS\|phong\|ten1\|ten2...` | Danh sách người trong phòng |
| `MSG` | `MSG\|phong\|nguoiGui\|thoiGian\|noiDung` | Tin mới (người gửi cũng nhận lại) |
| `USER_JOINED` | `USER_JOINED\|phong\|ten` | Có người vào phòng mình đang ở |
| `USER_LEFT` | `USER_LEFT\|phong\|ten` | Có người rời phòng / mất kết nối |
| `LEFT` | `LEFT\|phong` | Mình đã rời phòng |
| `ERROR` | `ERROR\|maLoi\|moTa` | Lỗi (xem bảng dưới) |
| `BYE` | `BYE\|lyDo` | Server sắp đóng kết nối |

**Khi JOIN thành công, server gửi theo thứ tự:** `JOINED` → các `HISTORY_ITEM` → `HISTORY_END` → `MEMBERS`, sau đó những người khác trong phòng nhận `USER_JOINED`, và mọi người nhận `ROOM_LIST` mới.

## 4. Mã lỗi

| Mã | Ý nghĩa |
|---|---|
| `NOT_LOGGED_IN` | Chưa đăng nhập |
| `ALREADY_LOGGED_IN` | Đã đăng nhập rồi |
| `INVALID_NAME` / `NAME_TAKEN` | Tên sai định dạng / đã có người dùng |
| `INVALID_ROOM` / `ROOM_EXISTS` / `ROOM_NOT_FOUND` | Tên phòng sai / trùng / không có |
| `ALREADY_IN_ROOM` / `NOT_IN_ROOM` | Đã ở trong phòng / không ở trong phòng |
| `EMPTY_MESSAGE` / `MESSAGE_TOO_LONG` | Tin rỗng / quá 1000 ký tự |
| `UNKNOWN_COMMAND` / `BAD_COMMAND` | Lệnh lạ / gói tin lỗi |
| `SERVER_ERROR` | Lỗi phía server |

## 5. Ví dụ phiên làm việc

```
C: LOGIN|Phat
S: LOGIN_OK|Phat
S: ROOM_LIST|Phong chung:0
C: JOIN|Phong chung
S: JOINED|Phong chung
S: HISTORY_ITEM|Phong chung|Bao|1759470000000|Chao ca nhom
S: HISTORY_END|Phong chung|1
S: MEMBERS|Phong chung|Phat
S: ROOM_LIST|Phong chung:1
C: MSG|Phong chung|Hello
S: MSG|Phong chung|Phat|1759470123456|Hello
C: LOGOUT
S: BYE|Tam biet
```
