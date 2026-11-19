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
    const daysAgo = days => new Date(Date.now() - days * 86_400_000).toISOString().slice(0, 10);
    const stays = roster.flatMap(([, , roomId], index) => roomId === null ? [] : [{
        id: index + 1, studentId: index + 1, roomId, checkInAt: daysAgo(14) + "T00:00:00.000Z"
    }]);
    const absences = [
        {id: 1, stayId: 1, absenceDate: daysAgo(0), reason: "Approved overnight leave", recordedBy: "Morgan Reed"},
        {id: 2, stayId: 4, absenceDate: daysAgo(1), reason: "Late return reported", recordedBy: "Morgan Reed"},
        {id: 3, stayId: 7, absenceDate: daysAgo(3), reason: "Off-campus placement", recordedBy: "System Administrator"}
    ];
    return {buildings, rooms, students, stays, absences, activeUser: null, nextId: 13};
}
