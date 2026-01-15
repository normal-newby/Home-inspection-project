import requests
from bs4 import BeautifulSoup
import sqlite3
import uuid
from dotenv import load_dotenv
import os

from definition_value_maker import choosePlace, chooseType

load_dotenv()

BASE_URL = os.getenv("BASE_URL")
API_URL = os.getenv("API_URL")

HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; InspectionBot/1.0)"
}

COOKIES = {
    
}

res = requests.get(BASE_URL, headers=HEADERS, cookies=COOKIES, timeout=10)
res.raise_for_status()

connector = sqlite3.connect("database.db")
cursor = connector.cursor()

def scrape():
    soup = BeautifulSoup(res.text, "html.parser")
    definitions = []

    allSections = soup.find(id="allSections")

    for section in allSections.find_all("div", class_="X", recursive=False):
        title = section.find("div", class_="Ex")
        if (title == None):
            continue

        name = title.find("span", style = lambda s: s and "float:left" in s).get_text(strip=True)

        con = section.find("div", class_="con")

        buttons = []
        for divWrap in con.find_all("div", class_="Z"):
            button = divWrap.find("button").get_text(strip=True)
            buttons.append(button)

        definitions.append({
            "fieldName": name,
            "buttons": buttons,
        })
    return definitions


if __name__ == "__main__":
    definitions = scrape()
    print(len(definitions))
    print(definitions)
    inspectionPlace = choosePlace()
    inspectionType = chooseType()

    for definition in definitions:
        fieldName = definition["fieldName"]
        buttons = definition["buttons"]

        fieldID = str(uuid.uuid4())
        cursor.execute(
            """
            INSERT INTO inspection_field_definition (id, field_name, field_place, field_type)
            VALUES (?, ?, ?, ?)
            """, (fieldID, fieldName, inspectionPlace, inspectionType)
        )
        
        for button in buttons:
            cursor.execute("""
                INSERT INTO inspection_field_definition_value (id, inspection_field_definition_id, value)
                VALUES (?, ?, ?)
            """, (str(uuid.uuid4()), fieldID, button)
            )

connector.commit()
connector.close()

print("Done")