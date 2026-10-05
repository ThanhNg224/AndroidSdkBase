# Git Workflow

> **Lưu ý về Repository Base Template (Giai đoạn Solo Maintainer):**
> - Đối với repo base template này, maintainer phát triển trực tiếp trên nhánh `main` để giữ quy trình tinh gọn.
> - Sau khi nhân bản/đổi tên thành dự án SDK thực tế qua `scripts/rename-project.sh`, toàn bộ quy trình phát triển và release của SDK sẽ vận hành đầy đủ theo mô hình GitFlow dưới đây.

## Branch Strategy (Gitflow)

| Branch | Mục đích | Tạo từ | Merge vào | Xóa sau merge |
| --- | --- | --- | --- | --- |
| `main` | Production-ready SDK code, gắn tag phát hành | — | — | Không |
| `develop` | Tích hợp các tính năng và module cho bản release tiếp theo | `main` | — | Không |
| `feature/<name>` | Phát triển tính năng mới (dùng `./scripts/new-feature.sh <name>`) | `develop` | `develop` | Có |
| `bugfix/<name>` | Sửa lỗi trong quá trình phát triển | `develop` | `develop` | Có |
| `release/<version>` | Chuẩn bị release (bump version, verify gates, apiDump) | `develop` | `main` & `develop` | Có |
| `hotfix/<name>` | Sửa lỗi khẩn cấp trên production SDK | `main` | `main` & `develop` | Có |

---

## Commits

Use Conventional Commits format with concise, imperative subjects:
```text
<type>(<scope>): <subject>
```

- `feat`: new capability or published API
- `fix`: bug fix or correctness correction
- `refactor`: internal restructuring without public API or behavior change
- `docs`: documentation or guidance updates
- `test`: test additions or fixture improvements
- `build` / `chore`: build logic, dependency bumps, or toolchain maintenance

---

## Quality Gates Before Merging

Follow [Verification](VERIFICATION.md) for local risk levels, project gates, and contract escalation before pushing.
