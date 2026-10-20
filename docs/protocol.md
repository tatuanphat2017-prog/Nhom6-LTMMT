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
| Đăng ký | `REGISTER\|ten\|matKhau` | Tạo tài khoản; thành công thì đăng nhập luôn (`LOGIN_OK`). Xem mục 8 |
| Đăng nhập | `LOGIN\|ten\|matKhau` | Tên 1–20 ký tự: chữ (có dấu), số, `_ . -`, không khoảng trắng; mật khẩu 6–64 ký tự |
| Danh sách phòng | `LIST_ROOMS` | |
| Tạo phòng | `CREATE_ROOM\|phong` | 1–30 ký tự, được có khoảng trắng ở giữa |
| Vào phòng | `JOIN\|phong` | Không phân biệt hoa/thường |
| Rời phòng | `LEAVE\|phong` | |
| Gửi tin | `MSG\|phong\|noiDung` | Tối đa 1000 ký tự, phải đang ở trong phòng |
| Xem lịch sử | `HISTORY\|phong\|soLuong` | 1–200, mặc định 50 |
| Thành viên phòng | `MEMBERS\|phong` | |
| Đăng xuất | `LOGOUT` | Server trả `BYE` rồi đóng kết nối |

Chưa `LOGIN` / `REGISTER` thành công thì mọi lệnh khác (trừ `LOGOUT`) đều bị trả `ERROR|NOT_LOGGED_IN`.

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
C: LOGIN|Phat|matkhau123
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

## 6. Truyền file (tối đa 25 MB/file)

File được cắt thành từng mảnh 4 KB, mỗi mảnh mã hóa **Base64** và gửi trong một dòng, nên vẫn dùng chung giao thức theo dòng và không dòng nào vượt 8192 ký tự. Base64 chỉ gồm `A-Z a-z 0-9 + / =`, không bao giờ chứa `|` hay xuống dòng.

**Giới hạn 25 MB được kiểm tra ở cả hai đầu:**
- Client chặn ngay khi người dùng chọn file lớn hơn 25 MB, không gửi gì lên mạng.
- Server kiểm tra dung lượng khai báo trong `FILE_START`, rồi đếm cả số byte thực nhận. Nếu vượt, server hủy ngay và xóa file tạm. Nhờ vậy client bị sửa code để gửi file 1–10 GB cũng không làm đầy ổ đĩa server.

### 6.1 Gửi file

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| C → S | `FILE_START\|maGui\|phong\|tenFile\|dungLuong` | Xin gửi file. `maGui` do client tự đặt (1–32 chữ/số) |
| S → C | `FILE_ACCEPT\|maGui` | Đồng ý, client bắt đầu gửi mảnh |
| C → S | `FILE_CHUNK\|maGui\|base64` | Một mảnh (tối đa 4096 byte trước khi mã hóa) |
| C → S | `FILE_END\|maGui` | Gửi xong |
| S → C | `FILE_SAVED\|maGui\|maFile` | Server đã lưu, `maFile` = 32 ký tự hex |
| S → phòng | `FILE\|phong\|nguoiGui\|thoiGian\|maFile\|tenFile\|dungLuong` | Báo cả phòng (kể cả người gửi) có file mới |
| S → C | `FILE_REJECT\|maGui\|maLoi\|moTa` | Từ chối hoặc hủy giữa chừng |
| C → S | `FILE_CANCEL\|maGui` | Người gửi bấm Hủy |

### 6.2 Tải file

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| C → S | `FILE_GET\|maFile` | Xin tải file |
| S → C | `FILE_DATA_START\|maFile\|dungLuong` | Bắt đầu gửi |
| S → C | `FILE_DATA\|maFile\|base64` | Một mảnh |
| S → C | `FILE_DATA_END\|maFile` | Gửi xong |
| S → C | `FILE_DATA_ERROR\|maFile\|maLoi\|moTa` | Không tải được |

Server gửi file trên một luồng riêng và tạm dừng khi hàng đợi gửi của client đó đầy, nên trong lúc tải người dùng vẫn chat bình thường.

### 6.3 Lịch sử

Tin gửi file nằm trong lịch sử phòng. Khi JOIN hoặc HISTORY, server gửi `HISTORY_FILE|phong|nguoiGui|thoiGian|maFile|tenFile|dungLuong` (xen kẽ với `HISTORY_ITEM` theo đúng thứ tự thời gian), nên người vào phòng sau vẫn tải được file.

**Nơi lưu:** nội dung file nằm trong thư mục `uploads/` cạnh nơi chạy server, tên file trên đĩa chính là `maFile`. Bảng `Messages` lưu `FileId`, `FileName`, `FileSize`, và `Content = [File] tên file`.

### 6.4 An toàn

- `maFile` chỉ được gồm 32 ký tự hex, nên không thể chèn `../` để đọc file ngoài thư mục `uploads/`.
- Tên file được làm sạch: bỏ đường dẫn (`..\..\evil.exe` thành `evil.exe`) và bỏ các ký tự Windows không cho phép.
- Mỗi client chỉ gửi 1 file và tải 1 file tại một thời điểm.
- Mất kết nối giữa chừng: server xóa file tạm, client xóa file `.part`.

### 6.5 Mã lỗi thêm

| Mã | Ý nghĩa |
|---|---|
| `FILE_TOO_LARGE` | Vượt 25 MB (theo khai báo hoặc theo số byte thực gửi) |
| `FILE_INVALID` | File rỗng, thiếu byte, dữ liệu Base64 lỗi |
| `FILE_BUSY` | Đang có một lần gửi/tải khác |
| `FILE_NOT_FOUND` | Mã file sai hoặc file không còn trên server |

### 6.6 Ví dụ

```
C: FILE_START|a1b2c3|Phong chung|Bao cao.pdf|10000
S: FILE_ACCEPT|a1b2c3
C: FILE_CHUNK|a1b2c3|JVBERi0xLjQK...      (3 mảnh: 4096 + 4096 + 1808 byte)
C: FILE_END|a1b2c3
S: FILE_SAVED|a1b2c3|8e1b9fb3a00c43beb8961a167dbe11ea
S: FILE|Phong chung|Phat|1792485000667|8e1b9fb3a00c43beb8961a167dbe11ea|Bao cao.pdf|10000
C: FILE_GET|8e1b9fb3a00c43beb8961a167dbe11ea         (người khác trong phòng)
S: FILE_DATA_START|8e1b9fb3a00c43beb8961a167dbe11ea|10000
S: FILE_DATA|8e1b9fb3a00c43beb8961a167dbe11ea|JVBERi0xLjQK...
S: FILE_DATA_END|8e1b9fb3a00c43beb8961a167dbe11ea
```

## 7. Người online và tin nhắn riêng

### 7.1 Danh sách người online

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| S → mọi người | `ONLINE_LIST\|ten1\|ten2...` | Gửi cho tất cả người đã đăng nhập mỗi khi có người đăng nhập hoặc thoát (sắp xếp A–Z, gồm cả chính mình) |
| C → S | `LIST_ONLINE` | Xin lại danh sách (client gọi khi vừa mở màn hình chính) |

### 7.2 Tin nhắn riêng

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| C → S | `PM\|nguoiNhan\|noiDung` | Gửi tin riêng cho một tài khoản (online **hoặc offline**, không phân biệt hoa/thường) |
| S → 2 người | `PM\|nguoiGui\|nguoiNhan\|thoiGian\|noiDung` | Server gửi cho **chỉ** người gửi và người nhận (người nhận offline thì chỉ lưu lại) |
| S → 2 người | `PM_FILE\|nguoiGui\|nguoiNhan\|thoiGian\|maFile\|tenFile\|dungLuong` | Có file riêng mới |
| C → S | `PM_HISTORY\|nguoiKia\|soLuong` | Xin lịch sử tin riêng với một người (1–200 tin, người kia có thể đang offline) |
| S → C | `PM_HISTORY_ITEM\|nguoiGui\|nguoiNhan\|thoiGian\|noiDung` | Một tin cũ (cũ → mới) |
| S → C | `PM_HISTORY_FILE\|nguoiGui\|nguoiNhan\|thoiGian\|maFile\|tenFile\|dungLuong` | Một tin file cũ |
| S → C | `PM_HISTORY_END\|nguoiKia\|soLuong` | Hết lịch sử |

**Gửi file riêng:** dùng lại đúng các bước ở mục 6.1, chỉ khác trường nơi nhận của `FILE_START` là `@ten` (ví dụ `FILE_START|a1b2|@Lang|baocao.pdf|10000`). Tên phòng không thể bắt đầu bằng `@` nên không bị nhầm. Lưu xong, server gửi `PM_FILE` cho 2 người thay vì `FILE` cho cả phòng.

**Lưu trữ:** bảng `PrivateMessages(PmId, Sender, Receiver, SentAt, Content, FileId, FileName, FileSize)`. Lịch sử lấy theo cả 2 chiều (A gửi B hoặc B gửi A), nên người thứ ba xin `PM_HISTORY` cũng chỉ thấy tin của chính họ.

**Mã lỗi thêm:**

| Mã | Ý nghĩa |
|---|---|
| `USER_NOT_FOUND` | Không có tài khoản người nhận |
| `SELF_MESSAGE` | Gửi tin hoặc file riêng cho chính mình |

### 7.3 Nhắn cho người offline và tin chưa đọc

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| S → C | `USER_LIST\|ten1\|ten2...` | Mọi tài khoản đã đăng ký (cả người offline). Gửi lúc đăng nhập và cho mọi người khi có tài khoản mới |
| C → S | `LIST_USERS` | Xin lại `USER_LIST` |
| S → C | `UNREAD_LIST\|nguoiGui:soTin\|...` | Số tin riêng **chưa đọc** theo từng người gửi, gồm cả tin nhận được trong lúc mình offline (ví dụ `UNREAD_LIST\|Phat:4\|Lang:1`). Gửi ngay sau khi đăng nhập |
| C → S | `LIST_UNREAD` | Xin lại `UNREAD_LIST` |
| C → S | `PM_READ\|nguoiKia` | Mình đã mở khung chat với `nguoiKia`, đánh dấu đã đọc mọi tin người đó gửi mình |

- Gửi tin hoặc file riêng cho người đang offline: server vẫn lưu vào bảng `PrivateMessages` với `IsRead = 0`. Người gửi vẫn nhận lại `PM` / `PM_FILE` như bình thường.
- Người nhận đăng nhập lại thì nhận `UNREAD_LIST`, client hiện **số đỏ bằng đúng số tin chưa đọc** cạnh tên người gửi. Bấm vào thì tải lịch sử (`PM_HISTORY`) và gửi `PM_READ`.
- Tin nhận được trong lúc đang online nhưng chưa mở khung chat cũng là chưa đọc. Đăng xuất rồi vào lại vẫn còn số đỏ.

### 7.4 Ví dụ

```
S: ONLINE_LIST|Lang|Phat                       (Lang vừa đăng nhập, cả 2 nhận)
C: PM|lang|Chao Lang
S: PM|Phat|Lang|1792487128945|Chao Lang        (Phat và Lang nhận, người khác không)
C: PM|Huy|alo
S: ERROR|USER_NOT_FOUND|'Huy' khong online
C: PM_HISTORY|Lang|50
S: PM_HISTORY_ITEM|Phat|Lang|1792487128945|Chao Lang
S: PM_HISTORY_END|Lang|1
```

## 8. Đăng ký và đăng nhập có mật khẩu

| Chiều | Gói tin | Ý nghĩa |
|---|---|---|
| C → S | `REGISTER\|ten\|matKhau` | Đăng ký. Thành công → `LOGIN_OK\|ten` (vào chat luôn) |
| C → S | `LOGIN\|ten\|matKhau` | Đăng nhập. Thành công → `LOGIN_OK\|tenGoc` (tên đúng hoa/thường như lúc đăng ký) |

**Lưu mật khẩu:** server **không** lưu mật khẩu dạng chữ thường. Mỗi tài khoản có một salt ngẫu nhiên 16 byte, mật khẩu được băm bằng **PBKDF2-HMAC-SHA256** lặp 65.536 lần (có sẵn trong Java), rồi lưu vào 2 cột `Users.PasswordHash`, `Users.PasswordSalt` dưới dạng Base64. Đăng nhập thì server băm lại mật khẩu gửi lên với đúng salt đó rồi so sánh.

**Chống dò:**
- Sai tên hay sai mật khẩu đều trả chung một lỗi `WRONG_LOGIN`. Với tên không tồn tại, server vẫn băm thử một lần, nên thời gian trả lời như nhau và không đoán được tên nào đã có.
- Sai quá 5 lần trên một kết nối thì server gửi `BYE` và ngắt.
- Mỗi lần băm mất khoảng 50 ms, nên đoán mật khẩu hàng loạt rất chậm.

**Tên cũ:** tên đã có trong bảng `Users` từ bản trước (chưa có mật khẩu) thì người đăng ký đầu tiên sẽ đặt mật khẩu cho tên đó. Lịch sử chat của tên đó vẫn giữ nguyên.

**Mã lỗi:**

| Mã | Ý nghĩa |
|---|---|
| `WRONG_LOGIN` | Sai tên đăng nhập hoặc mật khẩu |
| `USER_EXISTS` | Đăng ký tên đã có người dùng (không phân biệt hoa/thường) |
| `INVALID_PASSWORD` | Mật khẩu đăng ký ngắn hơn 6 hoặc dài hơn 64 ký tự |
| `NAME_TAKEN` | Tài khoản đang đăng nhập ở máy khác |

**Hạn chế:** kết nối TCP chưa mã hóa, nên mật khẩu đi trên mạng ở dạng chữ thường. Hướng phát triển là bọc socket bằng TLS (`SSLSocket`). Khi chạy qua ZeroTier thì đường truyền giữa các máy đã được ZeroTier mã hóa sẵn.
