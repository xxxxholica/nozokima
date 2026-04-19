# nozokima (覗き魔)

価値と資産の対比から消費の真実を可視化する、未来設計型家計管理システム。

## コンセプト
既存の家計簿アプリの多くは過去の支出記録を管理するだけに留まります。真に必要なのは、これからのお金をどう使うべきかという未来の判断基準です。
nozokimaは、ユーザーの購買意欲と現在の資産状況を客観的に分析し、衝動買いを抑制しつつ、意義のある消費をサポートするパーソナル・ファイナンシャル・アシスタントとして設計されています。

<div>
  <img width="150" alt="アプリホーム画面" src="https://github.com/user-attachments/assets/a2070e57-ba0d-4bc3-ad95-9fee85bd699d" />
  <img width="150" alt="AIアドバイス" src="https://github.com/user-attachments/assets/52cf2acf-8d35-4f2c-a70c-60aa1c7de799" />
</div>

## 主な機能
- **未来設計型アシスタント**
  いつまでにいくら貯めたいという希望的観測をGemini Nanoが分析。ユーザーのライフスタイルに合わせた複数の貯蓄シナリオを動的に提示します。

- **ローカル完結型セキュリティ**
  資産情報は最も機密性の高い個人情報です。外部APIを一切排除し、デバイス内の Gemini Nano (AICore) のみで推論を完結させることで、絶対的な安心感を提供します。

- **数式評価エンジン内蔵**
  入力中に複雑な計算をそのまま行えるエンジンを内蔵。家計簿特有の入力の手間を極限まで低減しています。

- **消費の真実を抽出**
  資産移動などの非消費活動を自動排除し、純粋な消費データのみをAIがスコアリング。投資か浪費かを客観的に提示し、衝動買いを未然に防ぎます。

<div>
  <img width="150" alt="目標設定" src="https://github.com/user-attachments/assets/4d185e19-92b8-47fe-9150-8e1e1ce4e310" />
  <img width="150" alt="プラン選択" src="https://github.com/user-attachments/assets/7f1d1e7e-f1d0-44ea-8b61-8bee2abeef31" />
  <img width="150" alt="目標詳細" src="https://github.com/user-attachments/assets/ba13320a-73f9-497e-955d-dee343425b9c" />
</div>

## 技術的挑戦
- **オフラインAIの最適化**: モバイルリソース環境でGemini Nanoのポテンシャルを最大限に引き出しつつ、UIのレスポンス性能を維持する設計。
- **堅牢なデータ管理**: Roomデータベースを用いて、複雑な収支・貸し借りの整合性を維持しつつ、安全にデータのエクスポート/インポートを実現。
- **OCRによる自動入力**: カメラからの読み取り精度を最大化し、記録のストレスを最小限に抑えるUXを追求。

<div>
  <img width="150" alt="OCR読み取り" src="https://github.com/user-attachments/assets/cef85e97-71b9-4344-b5e7-b10cf6d1bea4" />
  <img width="150" alt="AIフィードバック" src="https://github.com/user-attachments/assets/f544fb80-2400-48ac-abca-8f09d3a58df9" />
  <img width="150" alt="資産状況" src="https://github.com/user-attachments/assets/c9bb8ef4-118f-4a7e-a9c4-8a7def653158" />
</div>

## インストール方法
1. 本リポジトリの Releases から最新の `.apk` ファイルをダウンロードしてください。
2. Androidデバイスでファイルを開き、システム警告が出た場合は許可してインストールしてください。

## 動作要件
- Android 8.0 (API 26) 以上
- Gemini Nano（AICore）対応端末（Google Pixel 9 等で動作確認済み）

## 今後の展望
本アプリを単なる記録ツールから、人生の意思決定を支えるインフラへと進化させることを目指しています。現在は個人開発ですが、より洗練されたUI/UXの追求と、拡張性の高いアーキテクチャへのリファクタリングを継続しています。

---
*本アプリは開発中のパイロット版です。*
