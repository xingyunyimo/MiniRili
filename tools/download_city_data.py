#!/usr/bin/env python3
"""
生成全国县级行政区划数据 (modood/Administrative-divisions-of-China, MIT)
输出到 app/src/main/assets/county.json

用法：
  python tools/download_city_data.py

依赖：urllib（Python 标准库），无需第三方包。
"""
import json, os, re, sys, urllib.request

BASE = "https://gitee.com/modood/Administrative-divisions-of-China/raw/master/dist"
ASSETS = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")


def fetch_json(name):
    url = f"{BASE}/{name}"
    resp = urllib.request.urlopen(url, timeout=30)
    return json.loads(resp.read().decode("utf-8"))


def main():
    os.makedirs(ASSETS, exist_ok=True)

    # 1. 下载
    print("Downloading...")
    areas = fetch_json("areas.json")
    cities = fetch_json("cities.json")
    provinces = fetch_json("provinces.json")
    print(f"  areas: {len(areas)}, cities: {len(cities)}, provinces: {len(provinces)}")

    # 2. 代码映射
    prov_map = {p["code"]: p["name"] for p in provinces}
    city_map = {c["code"]: c for c in cities}

    # 3. 提取已有坐标
    db_path = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java", "com", "minirili", "app", "data", "weather", "ChineseCityDb.kt")
    with open(db_path, "r", encoding="utf-8") as f:
        content = f.read()
    entries = re.findall(
        r'Entry\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*([\d.]+),\s*([\d.]+)',
        content,
    )
    coord_map = {e[0]: {"lat": float(e[3]), "lon": float(e[4])} for e in entries}

    # 4. 省名映射（全称→简称）
    prov_full_to_short = {
        "北京市": "北京市", "天津市": "天津市", "上海市": "上海市", "重庆市": "重庆市",
        "河北省": "河北省", "山西省": "山西省", "辽宁省": "辽宁省", "吉林省": "吉林省",
        "黑龙江省": "黑龙江省", "江苏省": "江苏省", "浙江省": "浙江省", "安徽省": "安徽省",
        "福建省": "福建省", "江西省": "江西省", "山东省": "山东省", "河南省": "河南省",
        "湖北省": "湖北省", "湖南省": "湖南省", "广东省": "广东省", "海南省": "海南省",
        "四川省": "四川省", "贵州省": "贵州省", "云南省": "云南省", "陕西省": "陕西省",
        "甘肃省": "甘肃省", "青海省": "青海省", "台湾省": "台湾省",
        "内蒙古自治区": "内蒙古", "广西壮族自治区": "广西", "西藏自治区": "西藏",
        "宁夏回族自治区": "宁夏", "新疆维吾尔自治区": "新疆",
        "香港特别行政区": "香港", "澳门特别行政区": "澳门",
    }

    # 5. 省会坐标（省=直辖市时取直辖市名）
    prov_capital = {}
    for e in entries:
        name, prov = e[0], e[2]
        parent = e[5] if len(e) > 5 and e[5] else None
        if not parent and prov not in prov_capital:
            prov_capital[prov] = {"lat": float(e[3]), "lon": float(e[4])}

    # 6. 城市级坐标：只保留市级名称（避免同名区县如"市中区"共享同一坐标）
    city_only_names = {n for n in coord_map if n.endswith(("市", "州", "地区", "盟"))}

    city_coords = {}
    for city in cities:
        cn = city["name"]
        if cn in coord_map:
            city_coords[city["code"]] = coord_map[cn]
    for city in cities:
        if city["code"] not in city_coords:
            pc = city["provinceCode"]
            full_prov = prov_map.get(pc, "")
            short_prov = prov_full_to_short.get(full_prov, full_prov)
            if short_prov in prov_capital:
                city_coords[city["code"]] = prov_capital[short_prov]

    # 7. 生成输出
    output = []
    for area in areas:
        city_code = area["cityCode"]
        prov_code = area.get("provinceCode", city_code[:2])
        full_prov = prov_map.get(prov_code, "")
        short_prov = prov_full_to_short.get(full_prov, full_prov)
        city_name = city_map.get(city_code, {}).get("name", "")

        # 坐标解析：县级名只取城市级坐标，避免同名区县混淆
        coords = None
        if area["name"] in city_only_names:
            coords = coord_map.get(area["name"])
        if not coords:
            coords = city_coords.get(city_code)
        if not coords:
            coords = prov_capital.get(short_prov)
        if not coords:
            coords = {"lat": 39.9, "lon": 116.4}

        output.append({
            "name": area["name"],
            "province": short_prov,
            "city": city_name,
            "lat": coords["lat"],
            "lon": coords["lon"],
        })

    # 8. 地级市条目
    for city in cities:
        cn = city["name"]
        pc = city["provinceCode"]
        full_prov = prov_map.get(pc, "")
        short_prov = prov_full_to_short.get(full_prov, full_prov)
        coords = city_coords.get(city["code"], {"lat": 39.9, "lon": 116.4})
        output.append({
            "name": cn, "province": short_prov, "city": cn,
            "lat": coords["lat"], "lon": coords["lon"],
        })

    # 9. 直辖市条目
    for prov in provinces:
        pn = prov["name"]
        sn = prov_full_to_short.get(pn, pn)
        if pn in ("北京市", "天津市", "上海市", "重庆市"):
            coords = coord_map.get(sn, {"lat": 39.9, "lon": 116.4})
            output.append({
                "name": sn, "province": sn, "city": sn,
                "lat": coords["lat"], "lon": coords["lon"],
            })

    # 10. 去重（用 name+province+city 防止同名不同市条目被合并）
    seen = set()
    deduped = []
    for o in output:
        key = (o["name"], o["province"], o["city"])
        if key not in seen:
            seen.add(key)
            deduped.append(o)

    # 11. 写入
    dest = os.path.join(ASSETS, "county.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(deduped, f, ensure_ascii=False, separators=(",", ":"))

    defaults = sum(1 for o in deduped if o["lat"] == 39.9 and o["lon"] == 116.4)
    print(f"\nWritten: {dest}")
    print(f"  entries: {len(deduped)}")
    print(f"  provinces: {len(set(o['province'] for o in deduped))}")
    print(f"  with default coords: {defaults}")


if __name__ == "__main__":
    main()