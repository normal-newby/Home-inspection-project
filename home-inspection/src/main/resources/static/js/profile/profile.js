import { saveForm, loadForm } from "../formFactory.js";
import { autoSaveOnLeave } from "../ui/autoSave.js";

const URI = `/api/profile`;

const fields = ['name', 'company', 'phone', 'email', 'website', 'logoPath', 'inspectionNumber',
     'address', 'city', 'province', 'postalCode'
];

const saveBtn = document.getElementById("saveBtn");
const resetBtn = document.getElementById("resetBtn");

// The Google card saves its own settings as they change.
const autoSave = autoSaveOnLeave(document.getElementById("profileForm"),
    async () => Boolean(await saveForm(URI, fields)), { ignore: "#googleCalendarCard" });

saveBtn.addEventListener("click", async () => {
    if (await saveForm(URI, fields)) autoSave.markClean();
});
resetBtn.addEventListener("click", async () => {
    await loadForm(URI, fields);
    autoSave.markClean();
});

loadForm(URI, fields);
