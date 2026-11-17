#!/usr/bin/env python3
"""从酒店数据四表工作簿生成可复现的 H2 初始化数据。"""

from __future__ import annotations

import datetime as dt
import re
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal
from pathlib import Path


NAMESPACE = {
    "main": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
    "relationships": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
}
AMENITIES = {
    "健身室": ("gym", "gym"),
    "洗衣房": ("laundry", "laundry"),
    "停车场": ("parking", "parking"),
    "行李寄存": ("luggage_storage", "luggage_storage"),
    "机器人服务": ("robot_service", "robot_service"),
    "餐厅": ("restaurant", "restaurant"),
    "叫车服务": ("taxi_service", "taxi_service"),
    "会议室": ("meeting_room", "meeting_room"),
    "无障碍设施": ("accessibility", "accessibility"),
}
INVENTORY_DATES = (
    dt.date(2026, 11, 20),
    dt.date(2026, 11, 25),
    dt.date(2026, 11, 28),
    dt.date(2026, 11, 30),
)


def column_index(reference: str) -> int:
    letters = re.match(r"[A-Z]+", reference)
    if letters is None:
        raise ValueError(f"Invalid spreadsheet cell reference: {reference}")
    result = 0
    for letter in letters.group():
        result = result * 26 + ord(letter) - ord("A") + 1
    return result - 1


def load_workbook(path: Path) -> dict[str, list[dict[str, str | None]]]:
    with zipfile.ZipFile(path) as workbook:
        shared_strings: list[str] = []
        if "xl/sharedStrings.xml" in workbook.namelist():
            root = ET.fromstring(workbook.read("xl/sharedStrings.xml"))
            shared_strings = [
                "".join(text.text or "" for text in item.findall(".//main:t", NAMESPACE))
                for item in root.findall("main:si", NAMESPACE)
            ]

        workbook_xml = ET.fromstring(workbook.read("xl/workbook.xml"))
        relationships_xml = ET.fromstring(workbook.read("xl/_rels/workbook.xml.rels"))
        relationship_targets = {
            relationship.attrib["Id"]: relationship.attrib["Target"]
            for relationship in relationships_xml
        }
        sheets: dict[str, list[dict[str, str | None]]] = {}
        for sheet in workbook_xml.findall("main:sheets/main:sheet", NAMESPACE):
            relation_id = sheet.attrib[f"{{{NAMESPACE['relationships']}}}id"]
            target = relationship_targets[relation_id].lstrip("/")
            target = target if target.startswith("xl/") else f"xl/{target}"
            root = ET.fromstring(workbook.read(target))
            xml_rows = root.findall(".//main:sheetData/main:row", NAMESPACE)
            if not xml_rows:
                sheets[sheet.attrib["name"]] = []
                continue
            headers: list[str] = []
            parsed_rows: list[dict[str, str | None]] = []
            for row_number, row in enumerate(xml_rows):
                cells: dict[int, str | None] = {}
                for cell in row.findall("main:c", NAMESPACE):
                    value_node = cell.find("main:v", NAMESPACE)
                    value = value_node.text if value_node is not None else None
                    if cell.attrib.get("t") == "s" and value is not None:
                        value = shared_strings[int(value)]
                    elif cell.attrib.get("t") == "inlineStr":
                        value = "".join(text.text or "" for text in cell.findall(".//main:t", NAMESPACE))
                    if value == "":
                        value = None
                    cells[column_index(cell.attrib["r"])] = value
                if row_number == 0:
                    headers = [str(cells.get(index) or "") for index in range(max(cells) + 1)]
                    continue
                parsed_rows.append({
                    header: cells.get(index)
                    for index, header in enumerate(headers)
                    if header
                })
            sheets[sheet.attrib["name"]] = parsed_rows
        return sheets


def sql_string(value: str) -> str:
    parts = re.split(r"\r\n|\r|\n", value)
    quoted = [f"'{part.replace(chr(39), chr(39) * 2)}'" for part in parts]
    return quoted[0] if len(quoted) == 1 else f"CONCAT({', CHAR(10), '.join(quoted)})"


def sql_value(value: object | None) -> str:
    if value is None or value == "":
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, Decimal)):
        return str(value)
    return sql_string(str(value))


def decimal_value(value: str | None, zero_is_unknown: bool = False) -> Decimal | None:
    if value is None:
        return None
    result = Decimal(value)
    return None if zero_is_unknown and result == 0 else result


def boolean_value(value: str | None) -> bool | None:
    if value is None:
        return None
    if value in ("1", "1.0", "TRUE", "true"):
        return True
    if value in ("0", "0.0", "FALSE", "false"):
        return False
    raise ValueError(f"Unrecognized boolean value: {value}")


def integer_value(value: str | None) -> int | None:
    return None if value is None else int(Decimal(value))


def insert_batches(table: str, columns: list[str], rows: list[list[object | None]]) -> list[str]:
    statements: list[str] = []
    for offset in range(0, len(rows), 100):
        batch = rows[offset:offset + 100]
        values = ",\n".join(
            "(" + ", ".join(sql_value(value) for value in row) + ")"
            for row in batch
        )
        statements.append(
            f"MERGE INTO {table} ({', '.join(columns)}) KEY({columns[0]})\n"
            f"VALUES\n{values};"
        )
    return statements


def make_seed(sheets: dict[str, list[dict[str, str | None]]]) -> str:
    hotels_rows = sheets["酒店列表"]
    details_rows = sheets["酒店详情"]
    inventory_rows = sheets["酒店库存"]
    reviews_rows = sheets["评价"]

    hotel_ids = [row["id"] for row in hotels_rows]
    if any(source_id is None for source_id in hotel_ids) or len(set(hotel_ids)) != len(hotel_ids):
        raise ValueError("Hotel source IDs must be present and unique.")
    internal_ids = {source_id: index for index, source_id in enumerate(hotel_ids, start=1)}

    inventory_source_ids = list(dict.fromkeys(row["酒店id"] for row in inventory_rows))
    if len(inventory_source_ids) > len(hotel_ids) or len(inventory_source_ids) != 37:
        raise ValueError("The approved demo mapping expects exactly 37 inventory hotel IDs.")
    inventory_mapping = dict(zip(inventory_source_ids, hotel_ids[:len(inventory_source_ids)]))
    if any(row["酒店id"] not in inventory_mapping for row in inventory_rows):
        raise ValueError("An inventory hotel ID is missing from the approved mapping.")

    detail_by_id = {row["酒店id"]: row for row in details_rows}
    if set(detail_by_id) != set(hotel_ids):
        raise ValueError("Every hotel must have exactly one matching detail row.")
    known_review_hotel_ids = {row["酒店id"] for row in reviews_rows}
    if not known_review_hotel_ids.issubset(internal_ids):
        raise ValueError("A review references a hotel that is missing from the hotel list.")

    hotel_records: list[list[object | None]] = []
    detail_records: list[list[object | None]] = []
    for row in hotels_rows:
        source_id = row["id"]
        details = detail_by_id[source_id]
        true_amenities: list[str] = []
        amenity_values: dict[str, bool | None] = {}
        for source_column, (amenity, sql_column) in AMENITIES.items():
            available = boolean_value(details[source_column])
            amenity_values[sql_column] = available
            if available is True:
                true_amenities.append(amenity)
        rating = decimal_value(row["平均评分"], zero_is_unknown=True)
        hotel_records.append([
            internal_ids[source_id],
            source_id,
            row["酒店名"],
            row["城市"],
            row["省份"],
            row["区县"],
            row["地址"],
            rating,
            row["图片"],
            ",".join(true_amenities),
            None,
        ])
        detail_records.append([
            internal_ids[source_id],
            decimal_value(row["经度"]),
            decimal_value(row["纬度"]),
            integer_value(details["开业时间"]),
            integer_value(details["装修时间"]),
            details["联系电话"],
            *(amenity_values[column] for column in (
                "gym", "laundry", "parking", "luggage_storage", "robot_service",
                "restaurant", "taxi_service", "meeting_room", "accessibility"
            )),
        ])

    inventory_records: list[list[object | None]] = []
    for index, row in enumerate(inventory_rows, start=1):
        inventory_date = INVENTORY_DATES[(index - 1) % len(INVENTORY_DATES)]
        inventory_records.append([
            index,
            internal_ids[inventory_mapping[row["酒店id"]]],
            row["房间名"],
            decimal_value(row["房间价格"]),
            boolean_value(row["早餐"]),
            inventory_date,
            inventory_date,
            inventory_date,
            boolean_value(row["吹风机"]),
            boolean_value(row["毛巾"]),
            row["酒店id"],
        ])

    review_records: list[list[object | None]] = []
    for index, row in enumerate(reviews_rows, start=1):
        review_records.append([
            100000 + index,
            internal_ids[row["酒店id"]],
            row["评价id"],
            row["用户id"],
            row["用户名"],
            integer_value(row["评分"]),
            row["评价内容"],
            None,
        ])

    output = [
        "-- 由 backend/scripts/generate_seed.py 生成，请勿手工编辑。",
        "DELETE FROM review WHERE source_review_id IS NOT NULL;",
        "DELETE FROM room_offer WHERE id BETWEEN 1 AND 300;",
    ]
    output.extend(insert_batches(
        "hotel",
        ["id", "source_id", "name", "city", "province", "district", "address", "rating", "image", "amenities", "keywords"],
        hotel_records,
    ))
    output.extend(insert_batches(
        "hotel_detail",
        ["hotel_id", "longitude", "latitude", "opening_year",
         "renovation_year", "phone", "gym", "laundry", "parking", "luggage_storage",
         "robot_service", "restaurant", "taxi_service", "meeting_room", "accessibility"],
        detail_records,
    ))
    output.extend(insert_batches(
        "room_offer",
        ["id", "hotel_id", "room_name", "price_per_night", "breakfast", "available_from",
         "available_to", "available_on", "hair_dryer", "towels", "inventory_source_hotel_id"],
        inventory_records,
    ))
    output.extend(insert_batches(
        "review",
        ["id", "hotel_id", "source_review_id", "user_id", "user_name", "rating", "content", "created_at"],
        review_records,
    ))
    return "\n\n".join(output)


def main() -> None:
    repository_root = Path(__file__).resolve().parents[2]
    workbook_path = repository_root / "dataset" / "酒店数据四表.xlsx"
    output_path = repository_root / "backend" / "app" / "src" / "main" / "resources" / "data.sql"
    sheets = load_workbook(workbook_path)
    expected_sheets = {"酒店列表", "酒店详情", "酒店库存", "评价"}
    if not expected_sheets.issubset(sheets):
        raise ValueError("源工作簿缺少必需的四个工作表。")
    output_path.write_text(make_seed(sheets), encoding="utf-8")
    print(f"Generated {output_path.relative_to(repository_root)}")


if __name__ == "__main__":
    main()
