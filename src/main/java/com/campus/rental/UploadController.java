package com.campus.rental;

import static com.campus.rental.Rules.*;

import jakarta.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class UploadController {
  private final Store db;
  private final Path root;

  public UploadController(Store db, @Value("${rental.upload-dir}") String dir) {
    this.db = db;
    this.root = Path.of(dir).toAbsolutePath().normalize();
  }

  private String save(MultipartFile file, String directory) throws IOException {
    require(!file.isEmpty() && file.getSize() <= 5 * 1024 * 1024, "文件须为1字节至5MB的PNG或JPEG图片");
    byte[] bytes = file.getBytes();
    boolean png =
        bytes.length > 8
            && bytes[0] == (byte) 137
            && bytes[1] == 80
            && bytes[2] == 78
            && bytes[3] == 71
            && bytes[4] == 13
            && bytes[5] == 10
            && bytes[6] == 26
            && bytes[7] == 10;
    boolean jpg =
        bytes.length > 3
            && bytes[0] == (byte) 255
            && bytes[1] == (byte) 216
            && bytes[2] == (byte) 255;
    require(png || jpg, "只允许PNG或JPEG文件，文件内容与扩展名必须匹配");
    // 重新编码真实位图，拒绝伪造图片及混入的脚本载荷。
    try (var input =
        new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = javax.imageio.ImageIO.getImageReaders(input);
      require(readers.hasNext(), "图片无法解码");
      var reader = readers.next();
      try {
        reader.setInput(input);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        require(width > 0 && height > 0 && (long) width * height <= 20000000, "图片像素不能超过2000万");
        var image = reader.read(0);
        String ext = png ? "png" : "jpg", name = UUID.randomUUID() + "." + ext;
        Path dir = root.resolve(directory);
        Files.createDirectories(dir);
        javax.imageio.ImageIO.write(image, ext, dir.resolve(name).toFile());
        return name;
      } finally {
        reader.dispose();
      }
    }
  }

  @PostMapping("/me/certification")
  public Map<String, Object> certify(@RequestParam MultipartFile file, HttpSession s)
      throws IOException {
    WebConfig.student(s);
    long uid = WebConfig.uid(s);
    var profile = db.one("SELECT school,phone FROM t_user WHERE id=?", uid);
    require(
        !profile.get("school").toString().isBlank() && !profile.get("phone").toString().isBlank(),
        "请先保存学校与联系方式");
    String name = save(file, "private");
    db.update(
        "UPDATE t_user SET cert_file=?,certification='PENDING',cert_note=NULL WHERE id=?",
        name,
        uid);
    db.audit(uid, "CERT_SUBMIT", "USER", uid, "提交学生认证图片");
    return Map.of("message", "认证材料已提交");
  }

  @GetMapping("/certifications/{id}")
  public ResponseEntity<?> certification(@PathVariable long id, HttpSession s) throws IOException {
    long uid = WebConfig.uid(s);
    require(uid == id || WebConfig.admin(s), "无权查看认证材料");
    var row = db.one("SELECT cert_file FROM t_user WHERE id=?", id);
    require(row.get("cert_file") != null, "尚未提交材料");
    return file(row.get("cert_file").toString(), "private");
  }

  @PostMapping("/admin/images")
  public Map<String, Object> image(@RequestParam MultipartFile file) throws IOException {
    return Map.of("url", "/api/media/" + save(file, "public"));
  }

  @GetMapping("/media/{name}")
  public ResponseEntity<?> media(@PathVariable String name) throws IOException {
    return file(name, "public");
  }

  private ResponseEntity<?> file(String name, String directory) throws IOException {
    require(name.matches("[0-9a-f-]{36}\\.(png|jpg)"), "文件名无效");
    Path path = root.resolve(directory).resolve(name).normalize();
    require(path.startsWith(root.resolve(directory)) && Files.isRegularFile(path), "文件不存在");
    return ResponseEntity.ok()
        .contentType(name.endsWith("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
        .header("Cache-Control", "private, no-store")
        .body(new FileSystemResource(path));
  }
}
