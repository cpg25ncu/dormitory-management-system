/** The browser's local calendar day for a date, as YYYY-MM-DD. The demo runs in the viewer's time zone. */
export function localDay(value = new Date()) {
    const date = new Date(value);
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
}

export function demoFixtures() {
    const buildings = [
        {id: 1, name: "Cedar Hall", description: "North campus residence", managerId: 2},
        {id: 2, name: "Maple Hall", description: "East campus residence", managerId: null},
        {id: 3, name: "Willow Hall", description: "South campus residence", managerId: null}
    ];
    const rooms = [
        [1, 1, "101", 4], [2, 1, "102", 4], [3, 1, "201", 4],
        [4, 2, "110", 3], [5, 2, "111", 3], [6, 2, "210", 3],
        [7, 3, "A01", 2], [8, 3, "A02", 2]
    ].map(([id, buildingId, roomNumber, capacity]) => ({id, buildingId, roomNumber, capacity}));
    const roster = [
        ["Avery Chen", "Computer Science", 1],
        ["Clara Bennett", "Architecture", 1],
        ["Dylan Rivera", "Mechanical Engineering", 2],
        ["Elliot Park", "Data Science", 2],
        ["Harper Ellis", "Computer Science", 3],
        ["Jamie Patel", "Business", 4],
        ["Jordan Lee", "Design", 5],
        ["Micah Brooks", "Civil Engineering", 6],
        ["Noah Gomez", "Mathematics", 6],
        ["Robin Davis", "Computer Science", 7],
        ["Sasha Okafor", "Architecture", 8],
        ["Taylor Nguyen", "Information Systems", null]
    ];
    const students = roster.map(([fullName, program], index) => ({
        id: index + 1, studentNumber: "DEMO-" + String(index + 1).padStart(3, "0"), fullName, program
    }));
    const midnight = days => {
        const date = new Date();
        date.setHours(0, 0, 0, 0);
        date.setDate(date.getDate() - days);
        return date;
    };
    const daysAgo = days => localDay(midnight(days));
    const stays = roster.flatMap(([, , roomId], index) => roomId === null ? [] : [{
        id: index + 1, studentId: index + 1, roomId, checkInAt: midnight(14).toISOString()
    }]);
    const absences = [
        {id: 1, stayId: 1, absenceDate: daysAgo(0), reason: "Approved overnight leave", recordedBy: "Morgan Reed"},
        {id: 2, stayId: 4, absenceDate: daysAgo(1), reason: "Late return reported", recordedBy: "Morgan Reed"},
        {id: 3, stayId: 7, absenceDate: daysAgo(3), reason: "Off-campus placement", recordedBy: "System Administrator"}
    ];
    return {buildings, rooms, students, stays, absences, activeUser: null, nextId: 13};
}
